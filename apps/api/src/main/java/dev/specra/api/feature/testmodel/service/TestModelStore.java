package dev.specra.api.feature.testmodel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestModelJson;
import dev.specra.api.core.testmodel.TestModelValidation;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmodel.domain.TestModelVersion;
import dev.specra.api.feature.testmodel.domain.TestModelVersionRepository;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import dev.specra.api.feature.testmodel.dto.TestModelVersionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stored IR: read the current version, read the history, append the next version.
 *
 * <p>Kept apart from {@link TestModellingService} so the transaction around the write is short and
 * never wraps a model call — the round trip to the provider holds no database connection. Access is
 * checked by loading the case through {@link TestCaseService}, which is the one place that rule
 * lives.
 */
@Service
@Transactional(readOnly = true)
public class TestModelStore {

  private final TestModelVersionRepository repository;
  private final TestCaseService testCases;
  private final ProjectService projects;
  private final TestModelValidation validation;

  public TestModelStore(
      TestModelVersionRepository repository,
      TestCaseService testCases,
      ProjectService projects,
      TestModelValidation validation) {
    this.repository = repository;
    this.testCases = testCases;
    this.projects = projects;
    this.validation = validation;
  }

  public TestModelResponse current(UUID testCaseId) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    return repository
        .findFirstByTestCaseIdOrderByVersionDesc(testCaseId)
        .map(row -> TestModelViews.toResponse(row, testCase))
        .orElseThrow(() -> new ResourceNotFoundException("resource.test-model", testCaseId));
  }

  public TestModelResponse version(UUID testCaseId, int version) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    return repository
        .findByTestCaseIdAndVersion(testCaseId, version)
        .map(row -> TestModelViews.toResponse(row, testCase))
        .orElseThrow(() -> new ResourceNotFoundException("resource.test-model", testCaseId));
  }

  public List<TestModelVersionResponse> versions(UUID testCaseId) {
    testCases.get(testCaseId);
    return repository.findByTestCaseIdOrderByVersionDesc(testCaseId).stream()
        .map(TestModelViews::toVersion)
        .toList();
  }

  /**
   * Replaces the current Test Model with one a person edited, as a new version.
   *
   * <p>The point of the IR being reviewable is that a reviewer can correct it — a product that
   * shows someone a wrong step and offers only "generate again" has made the model the author and
   * the human the spectator, which is the inversion invariant 4 exists to prevent.
   *
   * <p>It edits rather than overwrites: a hand-edited IR is version n+1 with the previous one
   * intact, because regeneration and impact analysis both diff against what came before. The
   * document goes through {@link TestModelValidation} — the same schema, binding and semantic gates
   * a generated one does — so the hand-written path cannot be the one that stores a document the
   * adapter will choke on.
   */
  @Transactional
  public TestModelResponse replace(UUID testCaseId, JsonNode document) {
    TestCaseResponse testCase = testCases.get(testCaseId);
    projects.requireAccess(testCase.projectId(), Permission.CONTENT_EDIT);

    TestModelValidation.Result result = validation.validate(document);
    if (!result.ok()) {
      throw new TestModelInvalidException(result.violations());
    }
    return store(testCase, result.model());
  }

  /**
   * Appends the next version and records on the case that an IR now exists for its current text.
   * The caller has already validated the document through every layer; this only writes it.
   */
  @Transactional
  public TestModelResponse store(TestCaseResponse testCase, TestModel model) {
    String json;
    try {
      json = TestModelJson.write(model);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("A bound Test Model failed to serialise", e);
    }
    TestModelVersion row = new TestModelVersion();
    row.setWorkspaceId(projects.workspaceOf(testCase.projectId()));
    row.setTestCaseId(testCase.id());
    row.setVersion(
        repository
            .findFirstByTestCaseIdOrderByVersionDesc(testCase.id())
            .map(previous -> previous.getVersion() + 1)
            .orElse(1));
    row.setIrVersion(model.irVersion());
    row.setDocument(json);
    row.setChecksum(TestModelViews.sha256(json));
    TestModelVersion saved = repository.save(row);
    TestCaseResponse updated = testCases.markModelled(testCase.id());
    return TestModelViews.toResponse(saved, updated);
  }
}
