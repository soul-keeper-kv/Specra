package dev.specra.api.feature.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiFailures;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.ai.service.AiProviders;
import dev.specra.api.feature.analysis.domain.FailureAnalysis;
import dev.specra.api.feature.analysis.domain.FailureAnalysisRepository;
import dev.specra.api.feature.codegen.dto.CodeGenerationResponse;
import dev.specra.api.feature.codegen.service.CodeGenerationService;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.service.RunService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The second half of role 5: a reading becomes a patch a person can review.
 *
 * <p>It produces a {@code PROPOSED} row in {@code code_generations} and stops. Applying it is the
 * existing apply endpoint — invariant 4 has one enforcement point, and a repair that could commit
 * itself would be a second one.
 *
 * <p>The rule this class exists to hold is the refusal. A repair is offered only for a cause that
 * {@link dev.specra.api.feature.analysis.domain.RootCause#repairable()} allows: a product bug is
 * fixed in the application, not in the test, and an unclassifiable failure has nothing to patch
 * against. That check is here rather than only in the UI, because a UI check is a suggestion and
 * this is a rule.
 *
 * <p>Not {@code @Transactional}: the model call and the Git read must hold no database connection.
 */
@Service
public class RepairService {

  private static final Logger log = LoggerFactory.getLogger(RepairService.class);

  static final String SUBJECT_TYPE = "TEST_RUN_ITEM";

  private final FailureAnalysisRepository analyses;
  private final RunService runs;
  private final ProjectService projects;
  private final GitService git;
  private final CodeGenerationService codeGenerations;
  private final FailureAnalysisService.ChatClientPort chat;
  private final AiFailures failures;
  private final AiProviders providers;
  private final AiGenerationService generations;

  @Autowired
  public RepairService(
      FailureAnalysisRepository analyses,
      RunService runs,
      ProjectService projects,
      GitService git,
      CodeGenerationService codeGenerations,
      @Qualifier("modellingChatClient") org.springframework.ai.chat.client.ChatClient chatClient,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations) {
    this(
        analyses,
        runs,
        projects,
        git,
        codeGenerations,
        (system, user) -> chatClient.prompt().system(system).user(user).call().chatResponse(),
        failures,
        providers,
        generations);
  }

  RepairService(
      FailureAnalysisRepository analyses,
      RunService runs,
      ProjectService projects,
      GitService git,
      CodeGenerationService codeGenerations,
      FailureAnalysisService.ChatClientPort chat,
      AiFailures failures,
      AiProviders providers,
      AiGenerationService generations) {
    this.analyses = analyses;
    this.runs = runs;
    this.projects = projects;
    this.git = git;
    this.codeGenerations = codeGenerations;
    this.chat = chat;
    this.failures = failures;
    this.providers = providers;
    this.generations = generations;
  }

  /** Proposes a patch for the failure this analysis read. Writes nothing to the repository. */
  public CodeGenerationResponse propose(UUID analysisId) {
    FailureAnalysis analysis =
        analyses
            .findById(analysisId)
            .orElseThrow(
                () -> new ResourceNotFoundException("resource.failure-analysis", analysisId));
    projects.requireAccess(analysis.getProjectId(), Permission.CONTENT_EDIT);

    // Invariant 7, as a rule rather than as advice to the model. There is no argument a caller
    // can make that turns "your application regressed" into a diff.
    if (!analysis.getRootCause().repairable()) {
      throw new ConflictException("error.repair.not-repairable", analysis.getRootCause().name());
    }
    failures.requireConfigured();

    TestRunItem item = runs.requireItem(analysis.getTestRunItemId());
    String specPath = item.getSpecPath();
    String current = git.readFileOrNull(analysis.getProjectId(), specPath);
    if (current == null) {
      // The file the run executed is gone — renamed, deleted, or on another branch. Patching a
      // file we cannot read would mean inventing one.
      throw new ConflictException("error.repair.spec-missing", specPath);
    }

    Audit audit = new Audit(analysis);
    ChatResponse response =
        failures.guard(
            () -> chat.call(RepairPrompt.system(), RepairPrompt.user(analysis, specPath, current)));
    audit.add(response);

    Patch patch = Patch.parse(textOf(response));
    if (patch.problem() != null) {
      audit.record(AiGenerationStatus.FAILED, null, patch.problem());
      log.info("Repair for analysis {} was unusable: {}", analysisId, patch.problem());
      throw new ConflictException("error.repair.unusable");
    }

    // A model that returns the file untouched is telling us the diagnosis did not survive contact
    // with the code — which the prompt asks it to do rather than inventing a change. Storing that
    // as a proposal would put an empty diff in front of a reviewer.
    if (current.equals(patch.contents())) {
      audit.record(AiGenerationStatus.FAILED, null, patch.rationale());
      throw new ConflictException("error.repair.no-change");
    }

    UUID auditId = audit.record(AiGenerationStatus.PROPOSED, null, patch.rationale());
    Map<String, Object> file = new LinkedHashMap<>();
    file.put("path", specPath);
    file.put("role", "SPEC");
    file.put("contents", patch.contents());

    return codeGenerations.storeRepair(
        analysis.getTestCaseId(), analysis.getId(), auditId, List.of(file));
  }

  private static String textOf(ChatResponse response) {
    if (response == null || response.getResult() == null) {
      return "";
    }
    String text = response.getResult().getOutput().getText();
    return text == null ? "" : text;
  }

  /** The patched file, or the reason there is not one. */
  record Patch(String contents, String rationale, String problem) {

    static Patch parse(String text) {
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return new Patch(null, null, "the answer was not a JSON object");
      }
      JsonNode root;
      try {
        root = new ObjectMapper().readTree(text.substring(start, end + 1));
      } catch (JsonProcessingException e) {
        return new Patch(null, null, "the answer was not valid JSON: " + e.getOriginalMessage());
      }
      String contents = root.path("contents").asText("");
      if (!StringUtils.hasText(contents)) {
        // An empty file is never a repair. Committing one would delete the test.
        return new Patch(null, null, "the answer carried no file contents");
      }
      return new Patch(contents, root.path("rationale").asText("").trim(), null);
    }
  }

  /** Collects the tokens and latency of the call, and writes the one audit row for it. */
  private final class Audit {
    private final FailureAnalysis analysis;
    private final long started = System.nanoTime();
    private int promptTokens;
    private int completionTokens;
    private boolean counted;
    private String model;

    Audit(FailureAnalysis analysis) {
      this.analysis = analysis;
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

    UUID record(AiGenerationStatus status, UUID resultId, String rationale) {
      int latency = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
      return generations.record(
          new AiGenerationRecord(
              analysis.getWorkspaceId(),
              analysis.getProjectId(),
              AiGenerationKind.FIX,
              status,
              SUBJECT_TYPE,
              analysis.getTestRunItemId(),
              resultId,
              // The reading is the input to this call, so its id is what makes the proposal
              // traceable back to the evidence it was built from.
              analysis.getId().toString(),
              providers.chat(),
              model,
              counted ? promptTokens : null,
              counted ? completionTokens : null,
              latency,
              rationale));
    }
  }
}
