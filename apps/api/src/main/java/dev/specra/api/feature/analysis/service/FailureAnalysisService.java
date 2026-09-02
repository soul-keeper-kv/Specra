package dev.specra.api.feature.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.ConflictException;
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
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.service.RunService;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Role 5 of the pipeline: read the evidence from a failed cell and say what went wrong.
 *
 * <p>Two rules shape it. **Only FAILED is analysable** — ERROR means the run could not complete
 * (install, build, timeout, infrastructure), which is ours to fix and never worth a model call; V12
 * made that distinction a column precisely so this service could rely on it. And **an analysis is
 * stored, not recomputed**: the evidence is immutable once the run finished, so a second call would
 * spend money to produce a possibly different answer to an identical question.
 *
 * <p>Not {@code @Transactional} at class level: the round trip to the provider must hold no
 * database connection. The reads and the write are each their own short transaction.
 */
@Service
public class FailureAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(FailureAnalysisService.class);

  static final String SUBJECT_TYPE = "TEST_RUN_ITEM";

  private final FailureAnalysisRepository repository;
  private final RunService runs;
  private final ProjectService projects;
  private final TestCaseService testCases;
  private final EvidenceCollector evidence;
  private final ChatClientPort chat;
  private final AiFailures failures;
  private final AiProviders providers;
  private final AiGenerationService generations;
  private final ObjectMapper json;

  // Two constructors (the second takes the collapsed port, for tests), so Spring must be told.
  @Autowired
  public FailureAnalysisService(
      FailureAnalysisRepository repository,
      RunService runs,
      ProjectService projects,
      TestCaseService testCases,
      EvidenceCollector evidence,
      @Qualifier("modellingChatClient") org.springframework.ai.chat.client.ChatClient chatClient,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations,
      ObjectMapper json) {
    this(
        repository,
        runs,
        projects,
        testCases,
        evidence,
        (system, user) -> chatClient.prompt().system(system).user(user).call().chatResponse(),
        failures,
        providers,
        generations,
        json);
  }

  FailureAnalysisService(
      FailureAnalysisRepository repository,
      RunService runs,
      ProjectService projects,
      TestCaseService testCases,
      EvidenceCollector evidence,
      ChatClientPort chat,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations,
      ObjectMapper json) {
    this.repository = repository;
    this.runs = runs;
    this.projects = projects;
    this.testCases = testCases;
    this.evidence = evidence;
    this.chat = chat;
    this.failures = failures;
    this.providers = providers;
    this.generations = generations;
    this.json = json;
  }

  /** One model call, as the tests see it: the fluent client collapsed to its two inputs. */
  @FunctionalInterface
  interface ChatClientPort {
    ChatResponse call(String system, String user);
  }

  /** The stored reading, if this cell has one. */
  @Transactional(readOnly = true)
  public Optional<FailureAnalysisResponse> find(UUID itemId) {
    TestRunItem item = runs.requireItem(itemId);
    projects.requireAccess(projectOf(item), Permission.CONTENT_VIEW);
    return repository.findByTestRunItemId(itemId).map(FailureAnalysisService::toResponse);
  }

  /**
   * Analyses a failed cell, or returns the reading it already has.
   *
   * <p>Idempotent on purpose: the button is in a UI, the evidence cannot change, and a second press
   * should not cost a second call. {@code reanalyse} is the deliberate override, for when the first
   * answer was poor.
   */
  public FailureAnalysisResponse analyse(UUID itemId, boolean reanalyse) {
    TestRunItem item = runs.requireItem(itemId);
    UUID projectId = projectOf(item);
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);

    Optional<FailureAnalysis> existing = repository.findByTestRunItemId(itemId);
    if (existing.isPresent() && !reanalyse) {
      return toResponse(existing.get());
    }

    // ERROR is not a test failure: the suite never got far enough to say anything about the
    // application. Asking a model about a failed npm install spends money to be told nothing.
    if (item.getStatus() != ItemStatus.FAILED) {
      throw new ConflictException("error.analysis.not-failed", item.getStatus().name());
    }
    failures.requireConfigured();

    TestCaseResponse testCase = testCases.get(item.getTestCaseId());
    FailureEvidence bundle = evidence.collect(item, testCase);

    String system = FailureAnalysisPrompt.system();
    String user = FailureAnalysisPrompt.user(bundle);
    Audit audit = new Audit(projectId, item.getId(), sha256(user));

    ChatResponse response = failures.guard(() -> chat.call(system, user));
    audit.add(response);
    Answer answer = Answer.parse(textOf(response));

    if (answer.problem() != null) {
      // No repair round here, unlike modelling. There is no schema to satisfy — an unparseable
      // answer means the provider misbehaved, and asking the same question again is a coin flip
      // the user pays for. Better to say so and let them press the button.
      audit.record(AiGenerationStatus.FAILED, null, answer.problem());
      log.info("Failure analysis for item {} was unusable: {}", item.getId(), answer.problem());
      throw new ConflictException("error.analysis.unusable");
    }

    // The audit row first, because the analysis references it. Its id is assigned on save, so
    // there is nothing to point at until it exists — and a reading whose cost was never recorded
    // is exactly what the audit trail is for.
    UUID auditId = audit.record(AiGenerationStatus.PROPOSED, null, answer.rationale());
    FailureAnalysis stored = store(existing.orElse(null), item, projectId, answer, bundle, auditId);
    return toResponse(stored);
  }

  @Transactional
  FailureAnalysis store(
      FailureAnalysis existing,
      TestRunItem item,
      UUID projectId,
      Answer answer,
      FailureEvidence bundle,
      UUID generationId) {
    FailureAnalysis analysis = existing == null ? new FailureAnalysis() : existing;
    if (existing == null) {
      analysis.setWorkspaceId(projects.workspaceOf(projectId));
      analysis.setProjectId(projectId);
      analysis.setTestRunItemId(item.getId());
      analysis.setTestCaseId(item.getTestCaseId());
    }
    analysis.setGenerationId(generationId);
    analysis.setRootCause(answer.rootCause());
    analysis.setConfidence(answer.confidence());
    analysis.setSummary(answer.summary());
    analysis.setRationale(answer.rationale());
    // Invariant 7, enforced in the store rather than trusted to the prompt: a product bug and an
    // unclassifiable failure carry no suggestion, whatever the model felt like adding.
    analysis.setSuggestion(answer.rootCause().repairable() ? answer.suggestion() : null);
    analysis.setEvidence(write(bundle));
    return repository.save(analysis);
  }

  private String write(FailureEvidence bundle) {
    try {
      return json.writeValueAsString(bundle);
    } catch (JsonProcessingException e) {
      // The evidence is an audit aid, not the answer. Losing it must not lose the analysis.
      log.warn("Could not serialise the evidence bundle", e);
      return "{}";
    }
  }

  /** Through the run, which is the row that owns the project — the item only borrows it. */
  private static UUID projectOf(TestRunItem item) {
    return item.getRun().getProjectId();
  }

  private static String textOf(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return "";
    }
    String text = response.getResult().getOutput().getText();
    return text == null ? "" : text;
  }

  private static FailureAnalysisResponse toResponse(FailureAnalysis analysis) {
    return new FailureAnalysisResponse(
        analysis.getId(),
        analysis.getTestRunItemId(),
        analysis.getTestCaseId(),
        analysis.getRootCause(),
        analysis.getConfidence(),
        analysis.getSummary(),
        analysis.getRationale(),
        analysis.getSuggestion(),
        analysis.getRootCause().repairable(),
        analysis.getCreatedAt());
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JVM", e);
    }
  }

  /** What the model said, with everything it may not decide already clamped. */
  record Answer(
      RootCause rootCause,
      int confidence,
      String summary,
      String rationale,
      String suggestion,
      String problem) {

    static Answer parse(String text) {
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return problem("the answer was not a JSON object");
      }
      JsonNode root;
      try {
        root = new ObjectMapper().readTree(text.substring(start, end + 1));
      } catch (JsonProcessingException e) {
        return problem("the answer was not valid JSON: " + e.getOriginalMessage());
      }

      String summary = root.path("summary").asText("").trim();
      if (summary.isEmpty()) {
        return problem("the answer carried no summary");
      }
      // An unrecognised cause becomes UNKNOWN rather than a rejection: the useful part of the
      // answer is still the prose, and UNKNOWN is exactly what "I could not classify this" means.
      RootCause cause = causeOf(root.path("rootCause").asText(""));
      String suggestion = root.path("suggestion").asText("").trim();

      return new Answer(
          cause,
          clamp(root.path("confidence").asInt(0)),
          summary,
          root.path("rationale").asText("").trim(),
          suggestion.isEmpty() ? null : suggestion,
          null);
    }

    private static Answer problem(String message) {
      return new Answer(RootCause.UNKNOWN, 0, null, null, null, message);
    }

    private static RootCause causeOf(String raw) {
      if (!StringUtils.hasText(raw)) {
        return RootCause.UNKNOWN;
      }
      try {
        return RootCause.valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return RootCause.UNKNOWN;
      }
    }

    /** A model that answers 150 is not more certain than one that answers 100. */
    private static int clamp(int value) {
      return Math.max(0, Math.min(100, value));
    }
  }

  /** Collects the tokens and latency of the call, and writes the one audit row for it. */
  private final class Audit {
    private final UUID projectId;
    private final UUID itemId;
    private final String inputChecksum;
    private final long started = System.nanoTime();
    private int promptTokens;
    private int completionTokens;
    private boolean counted;
    private String model;

    Audit(UUID projectId, UUID itemId, String inputChecksum) {
      this.projectId = projectId;
      this.itemId = itemId;
      this.inputChecksum = inputChecksum;
    }

    void add(ChatResponse response) {
      if (response == null || response.getMetadata() == null) {
        return;
      }
      if (StringUtils.hasText(response.getMetadata().getModel())) {
        model = response.getMetadata().getModel();
      }
      Usage usage = response.getMetadata().getUsage();
      if (usage != null) {
        promptTokens += usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        completionTokens += usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        counted = true;
      }
    }

    /** Returns the row's id, which the analysis references — it exists only once saved. */
    UUID record(AiGenerationStatus status, UUID resultId, String rationale) {
      int latency = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
      return generations.record(
          new AiGenerationRecord(
              projects.workspaceOf(projectId),
              projectId,
              // FIX, because role 5 is the repair stage of the pipeline and this is its first
              // half — the reading a proposal is built on. A separate ANALYSIS kind would split
              // the cost of one decision across two buckets and make it unattributable.
              AiGenerationKind.FIX,
              status,
              SUBJECT_TYPE,
              itemId,
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
