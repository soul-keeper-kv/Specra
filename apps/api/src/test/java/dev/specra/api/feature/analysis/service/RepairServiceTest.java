package dev.specra.api.feature.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiFailures;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.ai.service.AiProviders;
import dev.specra.api.feature.analysis.domain.FailureAnalysis;
import dev.specra.api.feature.analysis.domain.FailureAnalysisRepository;
import dev.specra.api.feature.analysis.domain.RootCause;
import dev.specra.api.feature.codegen.service.CodeGenerationService;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.service.RunService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * The repair half of role 5.
 *
 * <p>Every test here is about what the service refuses to produce. A repair step that always
 * produces a diff is the failure mode the whole design is arranged against — invariant 7 — so the
 * refusals are the feature and the happy path is the easy part.
 */
@ExtendWith(MockitoExtension.class)
class RepairServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();
  private static final UUID ANALYSIS = UUID.randomUUID();

  private static final String SPEC = "tests/auth/login.spec.ts";
  private static final String CURRENT = "import { test } from '@playwright/test';\n// step [s3]\n";

  @Mock FailureAnalysisRepository analyses;
  @Mock RunService runs;
  @Mock ProjectService projects;
  @Mock GitService git;
  @Mock CodeGenerationService codeGenerations;
  @Mock AiFailures failures;
  @Mock AiProviders providers;
  @Mock AiGenerationService generations;

  final Deque<String> answers = new ArrayDeque<>();
  final List<String> prompts = new ArrayList<>();

  RepairService service;

  @BeforeEach
  void setUp() {
    service =
        new RepairService(
            analyses,
            runs,
            projects,
            git,
            codeGenerations,
            (system, user) -> {
              prompts.add(user);
              return new ChatResponse(List.of(new Generation(new AssistantMessage(answers.pop()))));
            },
            failures,
            providers,
            generations);

    lenient()
        .when(failures.guard(any()))
        .thenAnswer(inv -> inv.<Supplier<ChatResponse>>getArgument(0).get());
    lenient().when(providers.chat()).thenReturn("stub");
    lenient().when(generations.record(any())).thenReturn(UUID.randomUUID());
    lenient().when(runs.requireItem(ITEM)).thenReturn(item());
    lenient().when(git.readFileOrNull(PROJECT, SPEC)).thenReturn(CURRENT);
  }

  @Test
  void aPatchIsStoredAsAProposalAndNothingIsWritten() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.LOCATOR_DRIFT)));
    answers.add(
        """
        {"contents": "import { test } from '@playwright/test';\\n// fixed [s3]\\n",
         "rationale": "Selector now matches the renamed button."}
        """);

    service.propose(ANALYSIS);

    verify(projects).requireAccess(PROJECT, Permission.CONTENT_EDIT);
    // The proposal is stored; nothing reaches the repository until a person applies it.
    ArgumentCaptor<List<Map<String, Object>>> files = ArgumentCaptor.captor();
    verify(codeGenerations).storeRepair(eq(CASE), eq(ANALYSIS), any(), files.capture());
    assertThat(files.getValue())
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.get("path")).isEqualTo(SPEC);
              assertThat(file.get("role")).isEqualTo("SPEC");
              assertThat(String.valueOf(file.get("contents"))).contains("fixed [s3]");
            });

    AiGenerationRecord audit = recorded();
    assertThat(audit.kind()).isEqualTo(AiGenerationKind.FIX);
    assertThat(audit.status()).isEqualTo(AiGenerationStatus.PROPOSED);
    // The reading is the input, so the proposal is traceable back to the evidence it came from.
    assertThat(audit.inputChecksum()).isEqualTo(ANALYSIS.toString());
  }

  /**
   * Invariant 7 at the step that writes code. A product bug is fixed in the application, and no
   * caller can argue its way past that — the check is a rule here, not advice to the model.
   */
  @Test
  void aProductBugIsNeverRepaired() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.PRODUCT_BUG)));

    assertThatThrownBy(() -> service.propose(ANALYSIS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not-repairable");

    assertThat(prompts).isEmpty();
    verify(codeGenerations, never()).storeRepair(any(), any(), any(), anyList());
    verify(generations, never()).record(any());
  }

  /** An unclassifiable failure has nothing to patch against, so it is refused the same way. */
  @Test
  void anUnknownCauseIsNeverRepaired() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.UNKNOWN)));

    assertThatThrownBy(() -> service.propose(ANALYSIS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not-repairable");

    assertThat(prompts).isEmpty();
  }

  /**
   * The prompt asks a model that cannot fix the file honestly to return it unchanged. Storing that
   * would put an empty diff in front of a reviewer and call it a proposal.
   */
  @Test
  void anUnchangedFileIsNotStoredAsAProposal() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.TIMING)));
    answers.add(
        "{\"contents\": "
            + "\"import { test } from '@playwright/test';\\n// step [s3]\\n\", "
            + "\"rationale\": \"The code already waits correctly; the diagnosis does not hold.\"}");

    assertThatThrownBy(() -> service.propose(ANALYSIS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("no-change");

    verify(codeGenerations, never()).storeRepair(any(), any(), any(), anyList());
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.FAILED);
  }

  /** An empty file is never a repair: committing one would delete the test. */
  @Test
  void anEmptyFileIsRejected() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.TIMING)));
    answers.add("{\"contents\": \"\", \"rationale\": \"removed it\"}");

    assertThatThrownBy(() -> service.propose(ANALYSIS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("unusable");

    verify(codeGenerations, never()).storeRepair(any(), any(), any(), anyList());
  }

  /** Patching a file we cannot read would mean inventing one. */
  @Test
  void aMissingSpecFileIsRefusedRatherThanInvented() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.LOCATOR_DRIFT)));
    when(git.readFileOrNull(PROJECT, SPEC)).thenReturn(null);

    assertThatThrownBy(() -> service.propose(ANALYSIS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("spec-missing");

    assertThat(prompts).isEmpty();
  }

  /** The diagnosis is settled before this step; the prompt states it rather than re-asking. */
  @Test
  void thePromptCarriesTheDiagnosisAndTheFile() {
    when(analyses.findById(ANALYSIS)).thenReturn(Optional.of(analysis(RootCause.LOCATOR_DRIFT)));
    answers.add("{\"contents\": \"changed\", \"rationale\": \"r\"}");

    service.propose(ANALYSIS);

    assertThat(prompts)
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt).contains("LOCATOR_DRIFT");
              assertThat(prompt).contains("Update submitButton on LoginPage.");
              assertThat(prompt).contains(SPEC);
              assertThat(prompt).contains("step [s3]");
            });
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  private AiGenerationRecord recorded() {
    ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
    verify(generations).record(captor.capture());
    return captor.getValue();
  }

  private static FailureAnalysis analysis(RootCause cause) {
    FailureAnalysis analysis = new FailureAnalysis();
    analysis.setId(ANALYSIS);
    analysis.setWorkspaceId(WORKSPACE);
    analysis.setProjectId(PROJECT);
    analysis.setTestRunItemId(ITEM);
    analysis.setTestCaseId(CASE);
    analysis.setRootCause(cause);
    analysis.setConfidence(80);
    analysis.setSummary("The submit button moved.");
    analysis.setRationale("The error names a locator timeout.");
    analysis.setSuggestion("Update submitButton on LoginPage.");
    return analysis;
  }

  private static TestRunItem item() {
    TestRun run = new TestRun();
    run.setProjectId(PROJECT);

    TestRunItem item = new TestRunItem();
    item.setId(ITEM);
    item.setRun(run);
    item.setTestCaseId(CASE);
    item.setSpecPath(SPEC);
    return item;
  }
}
