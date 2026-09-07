package dev.specra.api.feature.codegen.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.codegen.domain.CodeGeneration;
import dev.specra.api.feature.codegen.domain.CodeGenerationRepository;
import dev.specra.api.feature.codegen.domain.CodeGenerationStatus;
import dev.specra.api.feature.codegen.dto.ApplyGenerationRequest;
import dev.specra.api.feature.codegen.dto.CodeGenerationResponse;
import dev.specra.api.feature.codegen.dto.EditedFileRequest;
import dev.specra.api.feature.git.dto.CommitRequest;
import dev.specra.api.feature.git.dto.CommitResponse;
import dev.specra.api.feature.git.dto.FileWriteRequest;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import dev.specra.api.feature.testmodel.service.TestModelStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The rule this feature exists to hold: AI proposes, a person disposes.
 *
 * <p>So the tests are mostly about what does <em>not</em> happen — generating writes nothing to the
 * repository, a refusal stores nothing, and a proposal that has already been decided cannot be
 * decided again.
 */
@ExtendWith(MockitoExtension.class)
class CodeGenerationServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();
  private static final UUID MODEL = UUID.randomUUID();
  private static final UUID REVIEWER = UUID.randomUUID();

  @Mock CodeGenerationRepository repository;
  @Mock TestCaseService testCases;
  @Mock TestModelStore models;
  @Mock ProjectService projects;
  @Mock GitService git;
  @Mock RunnerClient runner;
  @Mock dev.specra.api.feature.run.service.AutomationTestService automationTests;
  @Mock AiGenerationService generations;
  @Mock dev.specra.api.feature.pageobject.service.PageObjectService pageObjects;

  CodeGenerationService service;

  @BeforeEach
  void setUp() {
    service =
        new CodeGenerationService(
            repository,
            testCases,
            models,
            projects,
            git,
            automationTests,
            runner,
            generations,
            new PageObjectCatalogue(pageObjects),
            new ObjectMapper());
    lenient().when(testCases.get(CASE)).thenReturn(testCase(AutomationStatus.MODELLED));
    lenient().when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    lenient().when(models.current(CASE)).thenReturn(model());
    lenient()
        .when(repository.save(any(CodeGeneration.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    lenient().when(generations.record(any())).thenReturn(UUID.randomUUID());

    // Applying records who decided, so these tests need a signed-in reviewer — the decision is
    // the point of the feature and it must never be attributable to nobody.
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(REVIEWER, "qa@specra.dev", "QA"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void generatingStoresAProposalAndTouchesNoRepository() {
    when(runner.run(eq("codegen"), any())).thenReturn(runnerFiles());

    CodeGenerationResponse response = service.generate(CASE);

    assertThat(response.status()).isEqualTo("PROPOSED");
    assertThat(response.files()).extracting("path").contains("tests/auth/login.spec.ts");
    // The whole point: nothing was written and nothing was committed.
    verify(git, never()).writeFile(any(), any());
    verify(git, never()).commit(any(), any());
  }

  @Test
  void scaffoldsAgainWhenTheRepositoryHasAConfigButNoManifest() {
    when(runner.run(eq("codegen"), any())).thenReturn(runnerFiles());
    // The shape a copy is left in when the commit carrying the skeleton never reached the remote:
    // the config survived in a later commit, package.json did not. Asking only about the config
    // called this "already scaffolded" and generated three files into a project npm cannot
    // install — so the repository stayed unrunnable no matter how often it was regenerated.
    when(git.hasFile(PROJECT, "playwright.config.ts")).thenReturn(true);
    when(git.hasFile(PROJECT, "package.json")).thenReturn(false);

    service.generate(CASE);

    ArgumentCaptor<java.util.Map<String, Object>> payload =
        ArgumentCaptor.forClass(java.util.Map.class);
    verify(runner).run(eq("codegen"), payload.capture());
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> options =
        (java.util.Map<String, Object>) payload.getValue().get("options");
    assertThat(options.get("scaffold")).isEqualTo(true);
  }

  @Test
  void doesNotScaffoldWhenTheRepositoryIsAlreadyComplete() {
    when(runner.run(eq("codegen"), any())).thenReturn(runnerFiles());
    when(git.hasFile(PROJECT, "playwright.config.ts")).thenReturn(true);
    when(git.hasFile(PROJECT, "package.json")).thenReturn(true);

    service.generate(CASE);

    ArgumentCaptor<java.util.Map<String, Object>> payload =
        ArgumentCaptor.forClass(java.util.Map.class);
    verify(runner).run(eq("codegen"), payload.capture());
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> options =
        (java.util.Map<String, Object>) payload.getValue().get("options");
    assertThat(options.get("scaffold")).isEqualTo(false);
  }

  @Test
  void aFileTheRepositoryAlreadyHasIsReportedAsAModification() {
    when(runner.run(eq("codegen"), any())).thenReturn(runnerFiles());
    when(git.readFileOrNull(PROJECT, "tests/auth/login.spec.ts")).thenReturn("old contents");

    CodeGenerationResponse response = service.generate(CASE);

    assertThat(response.files())
        .filteredOn(file -> file.path().equals("tests/auth/login.spec.ts"))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.status()).isEqualTo("MODIFIED");
              assertThat(file.previous()).isEqualTo("old contents");
            });
  }

  @Test
  void theAuditRowSaysTheProjectionWasDeterministicRatherThanNamingAProvider() {
    when(runner.run(eq("codegen"), any())).thenReturn(runnerFiles());

    service.generate(CASE);

    ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
    verify(generations).record(captor.capture());
    AiGenerationRecord audit = captor.getValue();
    assertThat(audit.kind()).isEqualTo(AiGenerationKind.CODE);
    assertThat(audit.status()).isEqualTo(AiGenerationStatus.PROPOSED);
    assertThat(audit.provider()).isEqualTo("deterministic-adapter");
    assertThat(audit.promptTokens()).isNull();
  }

  @Test
  void aRefusalFromTheRunnerStoresNothingAndCarriesItsReason() {
    when(runner.run(eq("codegen"), any()))
        .thenReturn(
            RunnerClient.RunnerJobResult.refused(
                "generation-failed", "LoginPage has an element named \"page\""));

    assertThatThrownBy(() -> service.generate(CASE))
        .isInstanceOf(CodeGenerationFailedException.class)
        .satisfies(
            e ->
                assertThat(((CodeGenerationFailedException) e).extensions())
                    .containsEntry("runnerCode", "generation-failed"));

    verify(repository, never()).save(any());
    // The failure is still audited: a call that cost something must leave a row.
    ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
    verify(generations).record(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(AiGenerationStatus.FAILED);
  }

  @Test
  void anUninspectedPageTravelsWithTheProposalInsteadOfBeingGuessed() {
    when(runner.run(eq("codegen"), any()))
        .thenReturn(
            RunnerClient.RunnerJobResult.succeeded(
                Map.of(
                    "files",
                    List.of(
                        Map.of(
                            "path", "tests/auth/login.spec.ts", "role", "SPEC", "contents", "x")),
                    "unresolved",
                    List.of(
                        Map.of("stepId", "s5", "page", "DashboardPage", "element", "heading")))));

    CodeGenerationResponse response = service.generate(CASE);

    assertThat(response.unresolved())
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.page()).isEqualTo("DashboardPage");
              assertThat(target.element()).isEqualTo("heading");
            });
  }

  @Test
  void applyingWritesEveryFileAndCommitsExactlyThosePaths() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
    when(git.commit(eq(PROJECT), any(CommitRequest.class)))
        .thenReturn(new CommitResponse("abc1234", "test(auth): generate login from TC-104"));

    CodeGenerationResponse response = service.apply(proposal.getId(), null);

    ArgumentCaptor<FileWriteRequest> writes = ArgumentCaptor.forClass(FileWriteRequest.class);
    verify(git).writeFile(eq(PROJECT), writes.capture());
    assertThat(writes.getValue().path()).isEqualTo("tests/auth/login.spec.ts");

    ArgumentCaptor<CommitRequest> commit = ArgumentCaptor.forClass(CommitRequest.class);
    verify(git).commit(eq(PROJECT), commit.capture());
    // Exactly the generated paths — never everything that happens to be dirty.
    assertThat(commit.getValue().paths()).containsExactly("tests/auth/login.spec.ts");
    assertThat(commit.getValue().message()).startsWith("test(");

    assertThat(response.status()).isEqualTo("APPLIED");
    assertThat(response.commitSha()).isEqualTo("abc1234");
    verify(testCases).markCommitted(CASE);
  }

  @Test
  void applyingDoesNotPushUnlessAskedTo() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
    when(git.commit(eq(PROJECT), any())).thenReturn(new CommitResponse("abc", "m"));

    service.apply(proposal.getId(), null);

    verify(git, never()).push(any());
  }

  @Test
  void aProposalAlreadyDecidedCannotBeDecidedAgain() {
    CodeGeneration applied = proposal(CodeGenerationStatus.APPLIED);
    when(repository.findById(applied.getId())).thenReturn(Optional.of(applied));

    assertThatThrownBy(() -> service.apply(applied.getId(), null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("generation-not-proposed");

    verify(git, never()).writeFile(any(), any());
  }

  /**
   * Invariant 4 is only real if the reviewer can be the author. Shown a wrong line and offered
   * nothing but "generate again", they are a spectator.
   */
  @Test
  void aReviewerSEditIsWhatGetsCommitted() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
    when(git.commit(eq(PROJECT), any())).thenReturn(new CommitResponse("abc", "m"));

    service.apply(
        proposal.getId(),
        new ApplyGenerationRequest(
            null,
            null,
            List.of(new EditedFileRequest("tests/auth/login.spec.ts", "corrected by a human"))));

    ArgumentCaptor<FileWriteRequest> writes = ArgumentCaptor.forClass(FileWriteRequest.class);
    verify(git).writeFile(eq(PROJECT), writes.capture());
    assertThat(writes.getValue().content()).isEqualTo("corrected by a human");
  }

  /**
   * The stored proposal is the audit record of what the model produced. An edit that rewrote it
   * would leave {@code ai_generations} describing something nobody generated.
   */
  @Test
  void anEditDoesNotRewriteWhatTheModelProposed() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));
    when(git.commit(eq(PROJECT), any())).thenReturn(new CommitResponse("abc", "m"));

    service.apply(
        proposal.getId(),
        new ApplyGenerationRequest(
            null, null, List.of(new EditedFileRequest("tests/auth/login.spec.ts", "edited"))));

    assertThat(proposal.getFiles()).contains("\"contents\":\"code\"").doesNotContain("edited");
  }

  /**
   * Otherwise apply quietly becomes "commit any file I name", which is not what the reviewer looked
   * at and not what the proposal was reviewed as.
   */
  @Test
  void anEditToAFileTheProposalDoesNotContainIsRefused() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));

    assertThatThrownBy(
            () ->
                service.apply(
                    proposal.getId(),
                    new ApplyGenerationRequest(
                        null,
                        null,
                        List.of(new EditedFileRequest(".github/workflows/ci.yml", "x")))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("edit-unknown-path");

    verify(git, never()).writeFile(any(), any());
    verify(git, never()).commit(any(), any());
  }

  @Test
  void rejectingWritesNothingAndLeavesTheCaseAlone() {
    CodeGeneration proposal = proposal(CodeGenerationStatus.PROPOSED);
    when(repository.findById(proposal.getId())).thenReturn(Optional.of(proposal));

    CodeGenerationResponse response = service.reject(proposal.getId());

    assertThat(response.status()).isEqualTo("REJECTED");
    verify(git, never()).writeFile(any(), any());
    verify(git, never()).commit(any(), any());
    verify(testCases, never()).markCommitted(any());
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  private static RunnerClient.RunnerJobResult runnerFiles() {
    return RunnerClient.RunnerJobResult.succeeded(
        Map.of(
            "files",
            List.of(
                Map.of(
                    "path",
                    "tests/auth/login.spec.ts",
                    "role",
                    "SPEC",
                    "contents",
                    "import { test } from \"@playwright/test\";")),
            "unresolved",
            List.of()));
  }

  private CodeGeneration proposal(CodeGenerationStatus status) {
    CodeGeneration generation = new CodeGeneration();
    generation.setId(UUID.randomUUID());
    generation.setWorkspaceId(WORKSPACE);
    generation.setProjectId(PROJECT);
    generation.setTestCaseId(CASE);
    generation.setTestModelId(MODEL);
    generation.setGenerationId(UUID.randomUUID());
    generation.setStatus(status);
    generation.setAdapterVersion("0.1.0");
    generation.setFiles(
        "[{\"path\":\"tests/auth/login.spec.ts\",\"role\":\"SPEC\",\"contents\":\"code\"}]");
    generation.setUnresolved("[]");
    return generation;
  }

  /** The document is never read on this path — the runner is mocked — so a minimal one will do. */
  private static TestModelResponse model() {
    TestModel document =
        new TestModel(1, "Login with valid credentials", null, null, null, null, List.of(), null);
    return new TestModelResponse(
        MODEL, CASE, 1, 1, document, "sum", Instant.now(), List.of(), List.of());
  }

  private static TestCaseResponse testCase(AutomationStatus status) {
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
        status,
        false,
        List.of(new TestCaseStepResponse(1, "Open the login page", null, null)),
        Set.of("auth"),
        null,
        now,
        now);
  }
}
