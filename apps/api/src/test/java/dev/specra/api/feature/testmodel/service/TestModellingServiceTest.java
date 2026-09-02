package dev.specra.api.feature.testmodel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.security.Permission;
import dev.specra.api.core.testmodel.SchemaViolation;
import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestModelSchema;
import dev.specra.api.core.testmodel.TestModelSemantics;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiFailures;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.ai.service.AiProviders;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmodel.dto.AmbiguityQuestion;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
import org.springframework.core.io.ClassPathResource;

/**
 * The modelling loop with the model scripted: what it stores, what it refuses, and that it asks for
 * a repair exactly once. The schema and semantic layers are the real ones — that is the point.
 */
@ExtendWith(MockitoExtension.class)
class TestModellingServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();

  @Mock TestCaseService testCases;
  @Mock ProjectService projects;
  @Mock TestModelStore store;
  @Mock AiFailures failures;
  @Mock AiProviders providers;
  @Mock AiGenerationService generations;

  /** The scripted model: answers popped in order, prompts recorded for assertions. */
  final Deque<String> answers = new ArrayDeque<>();

  final List<String> prompts = new ArrayList<>();

  TestModellingService service;

  @BeforeEach
  void setUp() {
    service =
        new TestModellingService(
            testCases,
            projects,
            store,
            new TestModelSchema(),
            new TestModelSemantics(),
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
    lenient().when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    lenient().when(testCases.get(CASE)).thenReturn(loginCase());
  }

  @Test
  void aValidAnswerIsStoredAndAuditedAsProposed() throws IOException {
    answers.add(wrap(fixture("valid/login.json")));
    TestModelResponse stored = stored();
    when(store.store(eq(loginCase()), any(TestModel.class))).thenReturn(stored);

    TestModelResponse response = service.model(CASE);

    assertThat(response).isSameAs(stored);
    verify(projects).requireAccess(PROJECT, Permission.CONTENT_EDIT);
    assertThat(prompts).hasSize(1);
    assertThat(prompts.get(0)).contains("ts-1. Action: Open the login page").contains("Data: qa");
    AiGenerationRecord audit = recorded();
    assertThat(audit.status()).isEqualTo(AiGenerationStatus.PROPOSED);
    assertThat(audit.resultId()).isEqualTo(stored.id());
    assertThat(audit.provider()).isEqualTo("stub");
    assertThat(audit.workspaceId()).isEqualTo(WORKSPACE);
  }

  /** Fences are the most common decoration a model adds; they must not turn into a rejection. */
  @Test
  void codeFencesAroundTheAnswerAreTolerated() throws IOException {
    answers.add("```json\n" + fixture("valid/login.json") + "\n```");
    when(store.store(eq(loginCase()), any(TestModel.class))).thenReturn(stored());

    service.model(CASE);

    assertThat(prompts).hasSize(1);
  }

  /** A model that answers with the bare document instead of the envelope is still understood. */
  @Test
  void aBareTestModelWithoutTheEnvelopeIsAccepted() throws IOException {
    answers.add(fixture("valid/login.json"));
    when(store.store(eq(loginCase()), any(TestModel.class))).thenReturn(stored());

    service.model(CASE);

    assertThat(prompts).hasSize(1);
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.PROPOSED);
  }

  @Test
  void anAmbiguityIsRefusedWithItsQuestionsAndNothingIsStored() {
    answers.add(
        """
        {"ambiguities": [{"sourceStepId": "ts-2", "question": "Which field is the username?"}],
         "model": null}
        """);

    assertThatThrownBy(() -> service.model(CASE))
        .isInstanceOf(TestCaseAmbiguousException.class)
        .satisfies(
            e -> {
              var ex = (TestCaseAmbiguousException) e;
              assertThat(ex.questions())
                  .containsExactly(new AmbiguityQuestion("ts-2", "Which field is the username?"));
              assertThat(ex.extensions()).containsKey("questions");
            });
    verify(store, never()).store(any(), any());
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.FAILED);
    assertThat(recorded().rationale()).contains("ts-2: Which field is the username?");
  }

  /** The one repair round: the objections go back verbatim, and the corrected answer is stored. */
  @Test
  void aRejectedAnswerIsSentBackOnceWithTheObjections() throws IOException {
    answers.add(wrap(fixture("invalid/semantic/no-assertion-anywhere.json")));
    answers.add(wrap(fixture("valid/login.json")));
    when(store.store(eq(loginCase()), any(TestModel.class))).thenReturn(stored());

    service.model(CASE);

    assertThat(prompts).hasSize(2);
    assertThat(prompts.get(1))
        .contains("failed validation")
        .contains("/steps: the test asserts nothing");
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.PROPOSED);
  }

  @Test
  void aSecondRejectionIsTheEndWithTheViolationsAttached() throws IOException {
    answers.add(wrap(fixture("invalid/semantic/undeclared-parameter.json")));
    answers.add(wrap(fixture("invalid/schema/fill-without-a-value.json")));

    assertThatThrownBy(() -> service.model(CASE))
        .isInstanceOf(TestModelInvalidException.class)
        .satisfies(
            e -> {
              var ex = (TestModelInvalidException) e;
              assertThat(ex.violations()).isNotEmpty();
              assertThat(ex.extensions()).containsKey("violations");
            });
    assertThat(prompts).hasSize(2);
    verify(store, never()).store(any(), any());
    assertThat(recorded().status()).isEqualTo(AiGenerationStatus.FAILED);
  }

  @Test
  void proseInsteadOfJsonIsAValidationFailureNotACrash() {
    answers.add("I am not able to help with that.");
    answers.add("Still no.");

    assertThatThrownBy(() -> service.model(CASE))
        .isInstanceOf(TestModelInvalidException.class)
        .satisfies(
            e ->
                assertThat(((TestModelInvalidException) e).violations())
                    .extracting(SchemaViolation::path)
                    .containsExactly("/"));
  }

  /** Nothing to model is answered before a token is spent. */
  @Test
  void aCaseWithoutStepsIsAmbiguousWithoutCallingTheModel() {
    when(testCases.get(CASE)).thenReturn(withSteps(List.of()));

    assertThatThrownBy(() -> service.model(CASE)).isInstanceOf(TestCaseAmbiguousException.class);
    assertThat(prompts).isEmpty();
    verify(generations, never()).record(any());
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  private static String fixture(String name) throws IOException {
    return new String(
        new ClassPathResource("testmodel/fixtures/" + name).getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);
  }

  /** The output contract around a bare Test Model document. */
  private static String wrap(String model) {
    return "{\"ambiguities\": [], \"model\": " + model + "}";
  }

  private static TestCaseResponse loginCase() {
    return withSteps(
        List.of(
            new TestCaseStepResponse(1, "Open the login page", null, null),
            new TestCaseStepResponse(2, "Enter the username", "qa", null),
            new TestCaseStepResponse(3, "Enter the password", null, null),
            new TestCaseStepResponse(4, "Submit the form", null, null),
            new TestCaseStepResponse(5, "Check the dashboard", null, "The dashboard is shown")));
  }

  private static TestCaseResponse withSteps(List<TestCaseStepResponse> steps) {
    Instant now = Instant.parse("2026-09-01T00:00:00Z");
    return new TestCaseResponse(
        CASE,
        PROJECT,
        "TC-7",
        "Login with valid credentials",
        null,
        null,
        "The dashboard is shown",
        null,
        null,
        null,
        null,
        TestCasePriority.HIGH,
        AutomationStatus.NOT_AUTOMATED,
        false,
        steps,
        Set.of("auth"),
        null,
        now,
        now);
  }

  private static TestModelResponse stored() {
    return new TestModelResponse(
        UUID.randomUUID(), CASE, 1, 1, null, "abc", Instant.now(), List.of(), List.of());
  }

  private AiGenerationRecord recorded() {
    ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
    verify(generations).record(captor.capture());
    return captor.getValue();
  }
}
