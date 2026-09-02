package dev.specra.api.feature.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.specra.api.feature.analysis.dto.FailureAnalysisResponse;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.domain.ItemStatus;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.service.RunService;
import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Role 5 with the model scripted.
 *
 * <p>Most of these are about restraint rather than capability: what the service refuses to ask,
 * what it refuses to store, and the one answer — "your application is broken" — that a tool like
 * this has to be able to give without hedging it into a diff.
 */
@ExtendWith(MockitoExtension.class)
class FailureAnalysisServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();

  @Mock FailureAnalysisRepository repository;
  @Mock RunService runs;
  @Mock ProjectService projects;
  @Mock TestCaseService testCases;
  @Mock EvidenceCollector evidence;
  @Mock AiFailures failures;
  @Mock AiProviders providers;
  @Mock AiGenerationService generations;

  final Deque<String> answers = new ArrayDeque<>();
  final List<String> prompts = new ArrayList<>();

  FailureAnalysisService service;

  @BeforeEach
  void setUp() {
    service =
        new FailureAnalysisService(
            repository,
            runs,
            projects,
            testCases,
            evidence,
            (system, user) -> {
              prompts.add(user);
              return new ChatResponse(List.of(new Generation(new AssistantMessage(answers.pop()))));
            },
            failures,
            providers,
            generations,
            new ObjectMapper());

    lenient()
        .when(failures.guard(any()))
        .thenAnswer(inv -> inv.<Supplier<ChatResponse>>getArgument(0).get());
    lenient().when(providers.chat()).thenReturn("stub");
    lenient().when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    lenient().when(testCases.get(CASE)).thenReturn(testCase());
    lenient().when(evidence.collect(any(), any())).thenReturn(bundle());
    lenient().when(generations.record(any())).thenReturn(UUID.randomUUID());
    lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void aReadingIsStoredAndAuditedAgainstTheRunItem() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add(
        """
        {"rootCause": "LOCATOR_DRIFT", "confidence": 84, "summary": "The submit button moved.",
         "rationale": "The error names a locator timeout on getByRole('button').",
         "suggestion": "Update submitButton on LoginPage."}
        """);

    FailureAnalysisResponse response = service.analyse(ITEM, false);

    assertThat(response.rootCause()).isEqualTo(RootCause.LOCATOR_DRIFT);
    assertThat(response.confidence()).isEqualTo(84);
    assertThat(response.repairable()).isTrue();
    verify(projects).requireAccess(PROJECT, Permission.CONTENT_EDIT);

    AiGenerationRecord audit = recorded();
    assertThat(audit.kind()).isEqualTo(AiGenerationKind.FIX);
    assertThat(audit.status()).isEqualTo(AiGenerationStatus.PROPOSED);
    assertThat(audit.subjectId()).isEqualTo(ITEM);
    assertThat(audit.workspaceId()).isEqualTo(WORKSPACE);
  }

  /**
   * Invariant 7, and the reason this feature is worth building rather than dangerous. A model that
   * volunteers a fix for a genuine regression must not have it stored — the refusal is the answer.
   */
  @Test
  void aProductBugCarriesNoSuggestionEvenIfTheModelOffersOne() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add(
        """
        {"rootCause": "PRODUCT_BUG", "confidence": 91, "summary": "Checkout total is wrong.",
         "rationale": "The page shows 0.00 where the test expected 42.00.",
         "suggestion": "Relax the assertion to accept any total."}
        """);

    FailureAnalysisResponse response = service.analyse(ITEM, false);

    assertThat(response.rootCause()).isEqualTo(RootCause.PRODUCT_BUG);
    assertThat(response.suggestion()).isNull();
    assertThat(response.repairable()).isFalse();
  }

  /** UNKNOWN is a real answer too, and it must not become a proposal either. */
  @Test
  void anUnclassifiableFailureIsNotRepairable() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add(
        """
        {"rootCause": "SOMETHING_ELSE", "confidence": 30, "summary": "Not clear.",
         "rationale": "The evidence is thin.", "suggestion": "Have a look."}
        """);

    FailureAnalysisResponse response = service.analyse(ITEM, false);

    // An unrecognised cause degrades to UNKNOWN rather than being rejected: the prose is still
    // worth reading, and UNKNOWN is exactly what "I could not classify this" means.
    assertThat(response.rootCause()).isEqualTo(RootCause.UNKNOWN);
    assertThat(response.repairable()).isFalse();
    assertThat(response.suggestion()).isNull();
  }

  /**
   * ERROR means the run never got far enough to say anything about the application. Asking a model
   * about a failed npm install spends money to be told nothing.
   */
  @Test
  void aRunThatErroredIsNotAnalysed() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.ERROR));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.analyse(ITEM, false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not-failed");

    assertThat(prompts).isEmpty();
    verify(generations, never()).record(any());
  }

  /** The evidence cannot change once a run has finished, so a second press must cost nothing. */
  @Test
  void anExistingReadingIsReturnedWithoutCallingTheModelAgain() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.of(stored()));

    FailureAnalysisResponse response = service.analyse(ITEM, false);

    assertThat(response.rootCause()).isEqualTo(RootCause.TIMING);
    assertThat(prompts).isEmpty();
    verify(generations, never()).record(any());
  }

  /** …unless the reader says the first answer was poor. */
  @Test
  void reanalysingReplacesTheReadingRatherThanAddingASecondOne() {
    FailureAnalysis existing = stored();
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.of(existing));
    answers.add(
        """
        {"rootCause": "TEST_DATA", "confidence": 70, "summary": "QA_USER is missing.",
         "rationale": "The error names an empty credential.", "suggestion": "Set QA_USER."}
        """);

    service.analyse(ITEM, true);

    ArgumentCaptor<FailureAnalysis> saved = ArgumentCaptor.forClass(FailureAnalysis.class);
    verify(repository).save(saved.capture());
    // The same row, updated — not a second opinion sitting beside the first.
    assertThat(saved.getValue()).isSameAs(existing);
    assertThat(saved.getValue().getRootCause()).isEqualTo(RootCause.TEST_DATA);
  }

  /** An answer that is not usable is said so, rather than retried at the user's expense. */
  @Test
  void anUnparseableAnswerIsReportedAndAudited() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add("I had a look and it seems fine to me.");

    assertThatThrownBy(() -> service.analyse(ITEM, false))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("unusable");

    verify(repository, never()).save(any());
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.FAILED);
    // One call, not a silent retry loop (08-ai-pipeline.md, "what AI may never do", 6).
    assertThat(prompts).hasSize(1);
  }

  /** A confidence outside 0..100 is clamped: 150 is not more certain than 100. */
  @Test
  void anOutOfRangeConfidenceIsClamped() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add(
        """
        {"rootCause": "TIMING", "confidence": 150, "summary": "A race.",
         "rationale": "The assertion ran first.", "suggestion": "Wait for the state."}
        """);

    assertThat(service.analyse(ITEM, false).confidence()).isEqualTo(100);
  }

  /** The prompt carries the evidence, and the manual step is what makes the causes separable. */
  @Test
  void thePromptCarriesTheErrorAndTheManualStepItCameFrom() {
    when(runs.requireItem(ITEM)).thenReturn(item(ItemStatus.FAILED));
    when(repository.findByTestRunItemId(ITEM)).thenReturn(Optional.empty());
    answers.add(
        """
        {"rootCause": "LOCATOR_DRIFT", "confidence": 60, "summary": "s", "rationale": "r"}
        """);

    service.analyse(ITEM, false);

    assertThat(prompts)
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt).contains("locator timed out");
              assertThat(prompt).contains("LOCATOR_TIMEOUT");
              assertThat(prompt).contains("Click the submit button");
              assertThat(prompt).contains("TRACE");
            });
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  private AiGenerationRecord recorded() {
    ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
    verify(generations).record(captor.capture());
    return captor.getValue();
  }

  private static TestRunItem item(ItemStatus status) {
    TestRun run = new TestRun();
    run.setProjectId(PROJECT);

    TestRunItem item = new TestRunItem();
    item.setId(ITEM);
    item.setRun(run);
    item.setTestCaseId(CASE);
    item.setSpecPath("tests/auth/login.spec.ts");
    item.setBrowser("chromium");
    item.setStatus(status);
    item.setFailedStepId("s3");
    item.setErrorMessage("locator timed out");
    item.setErrorType("LOCATOR_TIMEOUT");
    return item;
  }

  private static FailureEvidence bundle() {
    return new FailureEvidence(
        "TC-104",
        "Login with valid credentials",
        "tests/auth/login.spec.ts",
        "chromium",
        "locator timed out",
        "LOCATOR_TIMEOUT",
        "s3",
        "Click the submit button (expected: the dashboard opens)",
        "click LoginPage.submitButton",
        "12: await page.submitButton.click();",
        List.of("TRACE", "VIDEO"));
  }

  private static FailureAnalysis stored() {
    FailureAnalysis analysis = new FailureAnalysis();
    analysis.setId(UUID.randomUUID());
    analysis.setProjectId(PROJECT);
    analysis.setTestRunItemId(ITEM);
    analysis.setTestCaseId(CASE);
    analysis.setRootCause(RootCause.TIMING);
    analysis.setConfidence(55);
    analysis.setSummary("A race.");
    analysis.setRationale("The assertion ran first.");
    return analysis;
  }

  private static TestCaseResponse testCase() {
    Instant now = Instant.parse("2026-09-02T00:00:00Z");
    return new TestCaseResponse(
        CASE,
        PROJECT,
        "TC-104",
        "Login with valid credentials",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        TestCasePriority.HIGH,
        AutomationStatus.COMMITTED,
        false,
        List.of(
            new TestCaseStepResponse(3, "Click the submit button", null, "the dashboard opens")),
        Set.of(),
        null,
        now,
        now);
  }
}
