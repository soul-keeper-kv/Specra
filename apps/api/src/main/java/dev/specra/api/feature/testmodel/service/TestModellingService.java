package dev.specra.api.feature.testmodel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.testmodel.SchemaViolation;
import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestModelJson;
import dev.specra.api.core.testmodel.TestModelSchema;
import dev.specra.api.core.testmodel.TestModelSemantics;
import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiFailures;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.ai.service.AiProviders;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmodel.dto.AmbiguityQuestion;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Roles 1 and 2 of the pipeline: from the manual case to a validated IR, or to a precise refusal.
 *
 * <p>The shape is the one 08-ai-pipeline.md prescribes and nothing looser: the model answers once;
 * the answer is validated schema → binding → semantics; a failure is fed back exactly once; a
 * second failure is {@code TEST_MODEL_INVALID} with the objections, and an ambiguity at any point
 * is {@code TEST_CASE_AMBIGUOUS} with the questions. Nothing is stored unless every layer passed,
 * and every outcome — stored, refused, rejected — leaves an {@code ai_generations} row behind.
 *
 * <p>Not {@code @Transactional}: the round trip to the provider must hold no database connection.
 * The read at the start and the write at the end are each their own short transaction.
 */
@Service
public class TestModellingService {

  private static final Logger log = LoggerFactory.getLogger(TestModellingService.class);

  static final String SUBJECT_TYPE = "TEST_CASE";

  private final TestCaseService testCases;
  private final ProjectService projects;
  private final TestModelStore store;
  private final TestModelSchema schema;
  private final TestModelSemantics semantics;
  private final ChatClientPort chat;
  private final AiFailures failures;
  private final AiProviders providers;
  private final AiGenerationService generations;

  // Two constructors (the second takes the collapsed port, for tests), so Spring must be told.
  @Autowired
  public TestModellingService(
      TestCaseService testCases,
      ProjectService projects,
      TestModelStore store,
      TestModelSchema schema,
      TestModelSemantics semantics,
      @Qualifier("modellingChatClient") org.springframework.ai.chat.client.ChatClient chatClient,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations) {
    this(
        testCases,
        projects,
        store,
        schema,
        semantics,
        (system, user) -> chatClient.prompt().system(system).user(user).call().chatResponse(),
        failures,
        providers,
        generations);
  }

  TestModellingService(
      TestCaseService testCases,
      ProjectService projects,
      TestModelStore store,
      TestModelSchema schema,
      TestModelSemantics semantics,
      ChatClientPort chat,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations) {
    this.testCases = testCases;
    this.projects = projects;
    this.store = store;
    this.schema = schema;
    this.semantics = semantics;
    this.chat = chat;
    this.failures = failures;
    this.providers = providers;
    this.generations = generations;
  }

  /** One model call, as the tests see it: the fluent client collapsed to its two inputs. */
  @FunctionalInterface
  interface ChatClientPort {
    ChatResponse call(String system, String user);
  }

  public TestModelResponse model(UUID testCaseId) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    projects.requireAccess(testCase.projectId(), Permission.CONTENT_EDIT);
    if (testCase.steps().isEmpty()) {
      throw new TestCaseAmbiguousException("error.test-case-ambiguous.no-steps", List.of());
    }
    failures.requireConfigured();

    String system = TestModelPrompt.system();
    String user = TestModelPrompt.user(testCase);
    Audit audit = new Audit(testCase, TestModelViews.sha256(user));

    Answer answer = ask(system, user, audit);
    refuseIfAmbiguous(answer, audit);
    Validated validated = validate(answer);
    if (!validated.ok()) {
      log.info(
          "Test Model for {} failed validation once ({} objections); asking for a repair",
          testCase.reference(),
          validated.violations().size());
      answer =
          ask(
              system,
              user + "\n\n" + TestModelPrompt.repair(answer.raw(), validated.violations()),
              audit);
      refuseIfAmbiguous(answer, audit);
      validated = validate(answer);
      if (!validated.ok()) {
        audit.record(AiGenerationStatus.FAILED, null, describe(validated.violations()));
        throw new TestModelInvalidException(validated.violations());
      }
    }

    TestModelResponse stored = store.store(testCase, validated.model());
    audit.record(AiGenerationStatus.PROPOSED, stored.id(), null);
    return stored;
  }

  private Answer ask(String system, String user, Audit audit) {
    ChatResponse response = failures.guard(() -> chat.call(system, user));
    audit.add(response);
    String text =
        response == null || response.getResult() == null
            ? ""
            : response.getResult().getOutput().getText();
    return Answer.parse(text == null ? "" : text);
  }

  private static void refuseIfAmbiguous(Answer answer, Audit audit) {
    if (answer.ambiguities().isEmpty()) {
      return;
    }
    audit.record(
        AiGenerationStatus.FAILED,
        null,
        answer.ambiguities().stream()
            .map(q -> (q.sourceStepId() == null ? "" : q.sourceStepId() + ": ") + q.question())
            .collect(Collectors.joining("\n")));
    throw new TestCaseAmbiguousException(answer.ambiguities());
  }

  /** Schema, then binding, then semantics — the order 02-test-model-ir.md fixes. */
  private Validated validate(Answer answer) {
    if (answer.problem() != null) {
      return Validated.invalid(List.of(new SchemaViolation("/", answer.problem())));
    }
    JsonNode node = answer.model();
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Validated.invalid(
          List.of(
              new SchemaViolation(
                  "/model", "no Test Model was produced and no ambiguity was reported")));
    }
    List<SchemaViolation> structural = schema.validate(node);
    if (!structural.isEmpty()) {
      return Validated.invalid(structural);
    }
    TestModel model;
    try {
      model = TestModelJson.read(node);
    } catch (JsonProcessingException e) {
      return Validated.invalid(
          List.of(new SchemaViolation("/", "does not bind: " + e.getOriginalMessage())));
    }
    List<SchemaViolation> semantic = semantics.validate(model);
    return semantic.isEmpty() ? Validated.ok(model) : Validated.invalid(semantic);
  }

  private static String describe(List<SchemaViolation> violations) {
    return violations.stream()
        .map(v -> v.path() + ": " + v.message())
        .collect(Collectors.joining("\n"));
  }

  /** What the model said, split into the two things the contract allows it to say. */
  record Answer(String raw, List<AmbiguityQuestion> ambiguities, JsonNode model, String problem) {

    static Answer parse(String text) {
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return new Answer(text, List.of(), null, "the answer was not a JSON object");
      }
      JsonNode root;
      try {
        root = TestModelJson.tree(text.substring(start, end + 1));
      } catch (JsonProcessingException e) {
        return new Answer(
            text, List.of(), null, "the answer was not valid JSON: " + e.getOriginalMessage());
      }
      // A bare document without the envelope is a model that skipped the wrapper, not a refusal.
      if (root.has("irVersion") && !root.has("model") && !root.has("ambiguities")) {
        return new Answer(text, List.of(), root, null);
      }
      List<AmbiguityQuestion> questions = new ArrayList<>();
      for (JsonNode item : root.path("ambiguities")) {
        String question = item.path("question").asText("").trim();
        if (!question.isEmpty()) {
          String stepId =
              item.path("sourceStepId").isTextual() ? item.get("sourceStepId").asText() : null;
          questions.add(new AmbiguityQuestion(stepId, question));
        }
      }
      return new Answer(text, List.copyOf(questions), root.get("model"), null);
    }
  }

  record Validated(TestModel model, List<SchemaViolation> violations) {
    static Validated ok(TestModel model) {
      return new Validated(model, List.of());
    }

    static Validated invalid(List<SchemaViolation> violations) {
      return new Validated(null, List.copyOf(violations));
    }

    boolean ok() {
      return violations.isEmpty();
    }
  }

  /** The audit line, accumulated across the one or two calls and written once with the outcome. */
  private final class Audit {
    private final TestCaseResponse testCase;
    private final String inputChecksum;
    private final long started = System.nanoTime();
    private int promptTokens;
    private int completionTokens;
    private boolean counted;
    private String model;

    Audit(TestCaseResponse testCase, String inputChecksum) {
      this.testCase = testCase;
      this.inputChecksum = inputChecksum;
    }

    void add(ChatResponse response) {
      if (response == null || response.getMetadata() == null) {
        return;
      }
      if (response.getMetadata().getModel() != null
          && !response.getMetadata().getModel().isBlank()) {
        model = response.getMetadata().getModel();
      }
      Usage usage = response.getMetadata().getUsage();
      if (usage != null) {
        promptTokens += usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        completionTokens += usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        counted = true;
      }
    }

    void record(AiGenerationStatus status, UUID resultId, String rationale) {
      int latency = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
      generations.record(
          new AiGenerationRecord(
              projects.workspaceOf(testCase.projectId()),
              testCase.projectId(),
              AiGenerationKind.MODEL,
              status,
              SUBJECT_TYPE,
              testCase.id(),
              resultId,
              inputChecksum,
              providers.chat(),
              model,
              counted ? promptTokens : null,
              counted ? completionTokens : null,
              latency,
              rationale));
    }
  }
}
