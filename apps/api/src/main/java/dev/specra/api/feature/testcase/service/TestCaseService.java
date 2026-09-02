package dev.specra.api.feature.testcase.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCase;
import dev.specra.api.feature.testcase.domain.TestCaseRepository;
import dev.specra.api.feature.testcase.domain.TestCaseStep;
import dev.specra.api.feature.testcase.dto.TestCaseRequest;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepRequest;
import dev.specra.api.feature.testcase.dto.TestCaseSummaryResponse;
import dev.specra.api.feature.testcase.mapper.TestCaseMapper;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The manual test case on its own: storage, search, references, the out-of-date flag.
 *
 * <p>It knows nothing about embeddings — changes are announced through {@link TestCaseEvents} and
 * {@link TestCaseIndexService} reacts after commit, the same shape the notes scaffold proved. It
 * reaches its parent through {@link ProjectService}, never that feature's repository.
 */
@Service
@Transactional(readOnly = true)
public class TestCaseService {

  private final TestCaseRepository repository;
  private final TestCaseMapper mapper;
  private final ProjectService projects;
  private final WorkspaceService workspaces;
  private final ApplicationEventPublisher events;

  public TestCaseService(
      TestCaseRepository repository,
      TestCaseMapper mapper,
      ProjectService projects,
      WorkspaceService workspaces,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.mapper = mapper;
    this.projects = projects;
    this.workspaces = workspaces;
    this.events = events;
  }

  public PageResponse<TestCaseSummaryResponse> search(
      UUID projectId, String q, String status, String tag, Pageable pageable) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    String query = StringUtils.hasText(q) ? q.trim() : null;
    String tagFilter = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
    return PageResponse.from(
        repository.search(projectId, query, parseStatus(status), tagFilter, pageable),
        mapper::toSummary);
  }

  /**
   * The status filter arrives as text because the web layer must not name a domain type. An unknown
   * value is the caller's mistake, reported exactly like a number that does not parse.
   */
  private static AutomationStatus parseStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    try {
      return AutomationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.detailKey());
    }
  }

  /**
   * Cross-project search for the content port; the UI always scopes by project instead.
   *
   * <p>Cross-project, never cross-tenant. This is the query the assistant runs on the user's
   * behalf, so it is narrowed to the workspaces that user belongs to — a retrieval step that
   * ignored tenancy would put somebody else's test cases into a prompt, and the answer would look
   * entirely plausible.
   */
  public PageResponse<TestCaseResponse> searchAll(String q, String tag, Pageable pageable) {
    List<UUID> visible = workspaces.visibleWorkspaceIds();
    if (visible.isEmpty()) {
      return PageResponse.from(Page.empty(pageable), mapper::toResponse);
    }
    String query = StringUtils.hasText(q) ? q.trim() : null;
    String tagFilter = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
    return PageResponse.from(
        repository.searchInWorkspaces(visible, query, tagFilter, pageable), mapper::toResponse);
  }

  public TestCaseResponse get(UUID id) {
    return mapper.toResponse(requireVisible(id, Permission.CONTENT_VIEW));
  }

  public TestCaseResponse findImported(UUID projectId, String externalSource, String externalId) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return repository
        .findByProjectIdAndExternalSourceAndExternalId(projectId, externalSource, externalId)
        .map(mapper::toResponse)
        .orElse(null);
  }

  /**
   * Loads a case only if the caller belongs to its workspace and may do this there.
   *
   * <p>The row carries {@code workspace_id} directly, so the check is one lookup and does not
   * depend on joining back up through the project — which is the reason V2 puts the column on every
   * table under a workspace in the first place.
   */
  private TestCase requireVisible(UUID id, Permission permission) {
    TestCase testCase = require(id);
    workspaces.requireAccess(testCase.getWorkspaceId(), permission);
    return testCase;
  }

  /**
   * The one throw site for a missing case. Package-private and returning the entity: it is for the
   * other services in this feature, never for the web layer — and it deliberately does not check
   * access, because its other callers run after commit, on behalf of nobody.
   */
  TestCase require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.testcase", id));
  }

  @Transactional
  public TestCaseResponse create(UUID projectId, TestCaseRequest request) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);

    TestCase testCase = new TestCase();
    testCase.setWorkspaceId(projects.workspaceOf(projectId));
    testCase.setProjectId(projectId);
    testCase.setReference(projects.nextTestCaseReference(projectId));
    apply(testCase, request);
    return mapper.toResponse(repository.save(testCase));
  }

  /** Creates the local automation input once while the external system remains authoritative. */
  @Transactional
  public TestCaseResponse importExternal(
      UUID projectId,
      TestCaseRequest request,
      String externalSource,
      String externalId,
      String externalUrl) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);
    TestCase existing =
        repository
            .findByProjectIdAndExternalSourceAndExternalId(projectId, externalSource, externalId)
            .orElse(null);
    if (existing != null) {
      return mapper.toResponse(existing);
    }

    TestCase testCase = new TestCase();
    testCase.setWorkspaceId(projects.workspaceOf(projectId));
    testCase.setProjectId(projectId);
    testCase.setReference(projects.nextTestCaseReference(projectId));
    testCase.setExternalSource(externalSource);
    testCase.setExternalId(externalId);
    testCase.setExternalUrl(externalUrl);
    testCase.setImportedAt(Instant.now());
    apply(testCase, request);
    return mapper.toResponse(repository.save(testCase));
  }

  @Transactional
  public TestCaseResponse update(UUID id, TestCaseRequest request) {
    TestCase testCase = requireVisible(id, Permission.CONTENT_EDIT);
    apply(testCase, request);
    // The intent changed after an IR was derived from it, so that IR is now behind the text.
    // A flag rather than a status: the case does not forget how far it had already got.
    if (testCase.getAutomationStatus() != AutomationStatus.NOT_AUTOMATED) {
      testCase.setOutOfDate(true);
    }
    testCase.setIndexedAt(null);
    TestCaseResponse response = mapper.toResponse(repository.save(testCase));
    events.publishEvent(new TestCaseEvents.TestCaseContentChanged(id));
    return response;
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(requireVisible(id, Permission.CONTENT_DELETE));
    events.publishEvent(new TestCaseEvents.TestCaseDeleted(id));
  }

  /** Records that the case's current text is in the vector store; {@link TestCaseIndexService}. */
  @Transactional
  TestCaseResponse markIndexed(UUID id) {
    TestCase testCase = require(id);
    testCase.setIndexedAt(Instant.now());
    return mapper.toResponse(repository.save(testCase));
  }

  /**
   * Records that an IR now exists for the case's current text; {@code TestModelStore} calls it in
   * the same transaction that stores the version. A case that had already got further keeps its
   * status — the code is now behind the new model, and regeneration is what catches it up.
   */
  @Transactional
  public TestCaseResponse markModelled(UUID id) {
    TestCase testCase = require(id);
    if (testCase.getAutomationStatus() == AutomationStatus.NOT_AUTOMATED) {
      testCase.setAutomationStatus(AutomationStatus.MODELLED);
    }
    testCase.setOutOfDate(false);
    return mapper.toResponse(repository.save(testCase));
  }

  /**
   * The case's code is in the repository now. Called when a person applies a generation, which is
   * the only path to this state — nothing sets it on the model's say-so.
   */
  @Transactional
  public TestCaseResponse markCommitted(UUID id) {
    TestCase testCase = require(id);
    testCase.setAutomationStatus(AutomationStatus.COMMITTED);
    testCase.setOutOfDate(false);
    return mapper.toResponse(repository.save(testCase));
  }

  private void apply(TestCase testCase, TestCaseRequest request) {
    testCase.setTitle(request.title().trim());
    testCase.setDescription(trimToNull(request.description()));
    testCase.setPreconditions(trimToNull(request.preconditions()));
    testCase.setExpectedResult(trimToNull(request.expectedResult()));
    if (request.priority() != null) {
      testCase.setPriority(request.priority());
    }
    testCase.replaceSteps(toSteps(request.steps()));
    testCase.replaceTags(request.tags());
  }

  private static List<TestCaseStep> toSteps(List<TestCaseStepRequest> requests) {
    List<TestCaseStep> steps = new ArrayList<>();
    if (requests == null) {
      return steps;
    }
    for (TestCaseStepRequest request : requests) {
      TestCaseStep step = new TestCaseStep();
      step.setActionText(request.action().trim());
      step.setTestData(trimToNull(request.data()));
      step.setExpectedText(trimToNull(request.expected()));
      steps.add(step);
    }
    return steps;
  }

  private static String trimToNull(String text) {
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
