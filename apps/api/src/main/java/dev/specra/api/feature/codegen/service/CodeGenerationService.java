package dev.specra.api.feature.codegen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.ai.domain.AiGenerationKind;
import dev.specra.api.feature.ai.domain.AiGenerationStatus;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import dev.specra.api.feature.ai.service.AiGenerationService;
import dev.specra.api.feature.codegen.domain.CodeGeneration;
import dev.specra.api.feature.codegen.domain.CodeGenerationRepository;
import dev.specra.api.feature.codegen.domain.CodeGenerationStatus;
import dev.specra.api.feature.codegen.dto.ApplyGenerationRequest;
import dev.specra.api.feature.codegen.dto.CodeGenerationResponse;
import dev.specra.api.feature.git.dto.CommitRequest;
import dev.specra.api.feature.git.dto.FileWriteRequest;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.service.AutomationTestService;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import dev.specra.api.feature.testmodel.service.TestModelStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role 4 of the pipeline: the IR becomes files, and a person decides what happens to them.
 *
 * <p>Deliberately thin on judgement. The projection is the runner's, and it is deterministic; the
 * rules here are the ones the product turns on — a proposal is never applied by the same call that
 * made it, an apply writes into the working copy and commits as the user, and a page nobody
 * inspected travels with the proposal rather than being silently guessed away.
 *
 * <p>Not {@code @Transactional} at the class level: the HTTP call to the runner must not hold a
 * database connection, and neither must a clone or a commit.
 */
@Service
public class CodeGenerationService {

  private static final Logger log = LoggerFactory.getLogger(CodeGenerationService.class);

  /** Bumped when the projection changes in a way that should invalidate stored proposals. */
  static final String ADAPTER_VERSION = "0.1.0";

  static final String SUBJECT_TYPE = "TEST_CASE";

  private final CodeGenerationRepository repository;
  private final TestCaseService testCases;
  private final TestModelStore models;
  private final ProjectService projects;
  private final GitService git;
  private final AutomationTestService automationTests;
  private final RunnerClient runner;
  private final AiGenerationService generations;
  private final PageObjectCatalogue pageObjects;
  private final ObjectMapper json;

  public CodeGenerationService(
      CodeGenerationRepository repository,
      TestCaseService testCases,
      TestModelStore models,
      ProjectService projects,
      GitService git,
      AutomationTestService automationTests,
      RunnerClient runner,
      AiGenerationService generations,
      PageObjectCatalogue pageObjects,
      ObjectMapper json) {
    this.repository = repository;
    this.testCases = testCases;
    this.models = models;
    this.projects = projects;
    this.git = git;
    this.automationTests = automationTests;
    this.runner = runner;
    this.generations = generations;
    this.pageObjects = pageObjects;
    this.json = json;
  }

  /**
   * Projects the case's current IR into files and stores the result as a proposal.
   *
   * <p>Nothing is written to the repository here — that is what {@link #apply} is for, and the
   * split is the point: there is no endpoint that generates and applies in one call.
   */
  public CodeGenerationResponse generate(UUID testCaseId) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    projects.requireAccess(testCase.projectId(), Permission.CONTENT_EDIT);

    // The IR is the input, so a case nobody has modelled is a 404 on the model, not a generation
    // that quietly produces nothing.
    TestModelResponse model = models.current(testCaseId);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model.document());
    payload.put("pages", pageObjects.forProject(testCase.projectId(), model));
    payload.put("flows", List.of());
    payload.put(
        "options",
        Map.of(
            "reference", testCase.reference(),
            "area", area(testCase),
            "adapterVersion", ADAPTER_VERSION,
            "scaffold", !git.hasFile(testCase.projectId(), "playwright.config.ts"),
            "projectName", "e2e-tests",
            "browsers", List.of("chromium")));

    long started = System.nanoTime();
    RunnerClient.RunnerJobResult job = runner.run("codegen", payload);
    int latency = (int) ((System.nanoTime() - started) / 1_000_000);

    UUID auditId =
        generations.record(
            new AiGenerationRecord(
                projects.workspaceOf(testCase.projectId()),
                testCase.projectId(),
                AiGenerationKind.CODE,
                job.ok() ? AiGenerationStatus.PROPOSED : AiGenerationStatus.FAILED,
                SUBJECT_TYPE,
                testCase.id(),
                null,
                model.checksum(),
                // Role 4 is the one step with no model call in it — that is the design, and the
                // audit row says so rather than naming a provider that was never asked.
                "deterministic-adapter",
                "playwright@" + ADAPTER_VERSION,
                null,
                null,
                latency,
                job.ok() ? null : job.message()));

    if (!job.ok()) {
      log.info(
          "Codegen refused for {}: {} — {}", testCase.reference(), job.errorCode(), job.message());
      throw new CodeGenerationFailedException(job.errorCode(), job.message());
    }

    return store(testCase, model, auditId, job.result());
  }

  @Transactional
  CodeGenerationResponse store(
      TestCaseResponse testCase,
      TestModelResponse model,
      UUID auditId,
      Map<String, Object> result) {
    // Only one proposal is live per case: an older one still marked PROPOSED would offer a
    // reviewer two different futures for the same file.
    for (CodeGeneration previous :
        repository.findByTestCaseIdAndStatus(testCase.id(), CodeGenerationStatus.PROPOSED)) {
      previous.setStatus(CodeGenerationStatus.SUPERSEDED);
    }

    CodeGeneration generation = new CodeGeneration();
    generation.setWorkspaceId(projects.workspaceOf(testCase.projectId()));
    generation.setProjectId(testCase.projectId());
    generation.setTestCaseId(testCase.id());
    generation.setTestModelId(model.id());
    generation.setGenerationId(auditId);
    generation.setStatus(CodeGenerationStatus.PROPOSED);
    generation.setAdapterVersion(ADAPTER_VERSION);
    generation.setFiles(write(result.get("files")));
    generation.setUnresolved(write(result.getOrDefault("unresolved", List.of())));
    CodeGeneration saved = repository.save(generation);

    return describe(saved, model.version(), testCase);
  }

  public CodeGenerationResponse current(UUID testCaseId) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    CodeGeneration generation =
        repository
            .findFirstByTestCaseIdAndStatusOrderByCreatedAtDesc(
                testCaseId, CodeGenerationStatus.PROPOSED)
            .orElseThrow(
                () -> new ResourceNotFoundException("resource.code-generation", testCaseId));
    return describe(generation, models.current(testCaseId).version(), testCase);
  }

  public List<CodeGenerationResponse> history(UUID testCaseId) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    return repository.findByTestCaseIdOrderByCreatedAtDesc(testCaseId).stream()
        .map(generation -> describe(generation, 0, testCase))
        .toList();
  }

  /**
   * The transition the whole product is arranged around: a person accepts, and only then does
   * anything reach the repository.
   *
   * <p>Writes every proposed file into the working copy, commits exactly those paths as the user,
   * and records who decided. A dirty working copy or a rejected push comes back as the error {@code
   * GitService} already defines — this method adds no way around either.
   */
  public CodeGenerationResponse apply(UUID generationId, ApplyGenerationRequest request) {
    CodeGeneration generation = require(generationId);
    TestCaseResponse testCase = testCases.get(generation.getTestCaseId());
    projects.requireAccess(generation.getProjectId(), Permission.CONTENT_EDIT);
    requireProposed(generation);

    List<Map<String, Object>> files = read(generation.getFiles());
    List<String> paths = new ArrayList<>();
    for (Map<String, Object> file : files) {
      String path = String.valueOf(file.get("path"));
      git.writeFile(
          generation.getProjectId(),
          new FileWriteRequest(path, String.valueOf(file.get("contents"))));
      paths.add(path);
    }

    String message =
        request != null && request.message() != null && !request.message().isBlank()
            ? request.message().trim()
            : CommitMessages.forGeneration(testCase);
    var commit = git.commit(generation.getProjectId(), new CommitRequest(message, paths));
    if (request != null && request.pushOrDefault()) {
      git.push(generation.getProjectId());
    }

    // The case now has code in the repository, and a run needs to know which file holds it.
    // Recorded here rather than at generation time because until a person applied it, nothing
    // was in the repository to run.
    specPathOf(files)
        .ifPresent(
            specPath ->
                automationTests.record(
                    projects.workspaceOf(generation.getProjectId()),
                    testCase.id(),
                    specPath,
                    testCase.title(),
                    generation.getTestModelId(),
                    commit.sha()));

    return complete(generation.getId(), CodeGenerationStatus.APPLIED, commit.sha(), testCase);
  }

  /**
   * The spec file among the generated ones — the file that actually holds the test.
   *
   * <p>A generation writes page objects, fixtures and config too; only the SPEC is what a run
   * executes. Empty when a generation somehow produced none, in which case nothing is recorded and
   * the case simply stays unrunnable rather than pointing a run at a page object.
   */
  private static java.util.Optional<String> specPathOf(List<Map<String, Object>> files) {
    return files.stream()
        .filter(file -> "SPEC".equals(String.valueOf(file.get("role"))))
        .map(file -> String.valueOf(file.get("path")))
        .findFirst();
  }

  public CodeGenerationResponse reject(UUID generationId) {
    CodeGeneration generation = require(generationId);
    projects.requireAccess(generation.getProjectId(), Permission.CONTENT_EDIT);
    requireProposed(generation);
    return complete(
        generation.getId(),
        CodeGenerationStatus.REJECTED,
        null,
        testCases.get(generation.getTestCaseId()));
  }

  @Transactional
  CodeGenerationResponse complete(
      UUID id, CodeGenerationStatus status, String commitSha, TestCaseResponse testCase) {
    CodeGeneration generation = require(id);
    generation.setStatus(status);
    generation.setCommitSha(commitSha);
    generation.setDecidedBy(CurrentUser.requireId());
    generation.setDecidedAt(Instant.now());
    if (status == CodeGenerationStatus.APPLIED) {
      testCases.markCommitted(testCase.id());
    }
    return describe(repository.save(generation), 0, testCase);
  }

  private CodeGeneration require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.code-generation", id));
  }

  private static void requireProposed(CodeGeneration generation) {
    if (generation.getStatus() != CodeGenerationStatus.PROPOSED) {
      throw new BusinessException(
          ErrorCode.GENERATION_NOT_PROPOSED,
          ErrorCode.GENERATION_NOT_PROPOSED.detailKey(),
          generation.getStatus().name());
    }
  }

  /**
   * Fills in what the reviewer sees, including the current contents of each file so the UI can
   * render a diff without a second round trip per path.
   */
  private CodeGenerationResponse describe(
      CodeGeneration generation, int modelVersion, TestCaseResponse testCase) {
    List<Map<String, Object>> files = read(generation.getFiles());
    List<dev.specra.api.feature.codegen.dto.GeneratedFileResponse> described = new ArrayList<>();
    for (Map<String, Object> file : files) {
      String path = String.valueOf(file.get("path"));
      String contents = String.valueOf(file.get("contents"));
      String previous =
          generation.getStatus() == CodeGenerationStatus.PROPOSED
              ? git.readFileOrNull(generation.getProjectId(), path)
              : null;
      String status =
          previous == null ? "NEW" : previous.equals(contents) ? "UNCHANGED" : "MODIFIED";
      described.add(
          new dev.specra.api.feature.codegen.dto.GeneratedFileResponse(
              path, String.valueOf(file.getOrDefault("role", "SPEC")), status, contents, previous));
    }

    List<dev.specra.api.feature.codegen.dto.UnresolvedTargetResponse> unresolved =
        read(generation.getUnresolved()).stream()
            .map(
                entry ->
                    new dev.specra.api.feature.codegen.dto.UnresolvedTargetResponse(
                        String.valueOf(entry.get("stepId")),
                        String.valueOf(entry.get("page")),
                        entry.get("element") == null ? null : String.valueOf(entry.get("element"))))
            .toList();

    return new CodeGenerationResponse(
        generation.getId(),
        generation.getTestCaseId(),
        testCase.reference(),
        generation.getStatus().name(),
        modelVersion,
        generation.getAdapterVersion(),
        List.copyOf(described),
        unresolved,
        generation.getCommitSha(),
        generation.getCreatedAt(),
        generation.getDecidedAt());
  }

  /** The case's first tag becomes the spec's folder, so specs group the way test cases do. */
  private static String area(TestCaseResponse testCase) {
    return testCase.tags().stream().sorted().findFirst().orElse(null);
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("A runner result failed to serialise", e);
    }
  }

  private List<Map<String, Object>> read(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("A stored generation is no longer readable", e);
    }
  }
}
