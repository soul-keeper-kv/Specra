package dev.specra.api.feature.testcase.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher events;

  public TestCaseService(
      TestCaseRepository repository,
      TestCaseMapper mapper,
      ProjectService projects,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.mapper = mapper;
    this.projects = projects;
    this.events = events;
  }

  public PageResponse<TestCaseSummaryResponse> search(
      UUID projectId, String q, String status, String tag, Pageable pageable) {
    projects.requireExists(projectId);
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

  /** Cross-project search for the content port; the UI always scopes by project instead. */
  public PageResponse<TestCaseResponse> searchAll(String q, String tag, Pageable pageable) {
    String query = StringUtils.hasText(q) ? q.trim() : null;
    String tagFilter = StringUtils.hasText(tag) ? tag.trim().toLowerCase() : null;
    return PageResponse.from(
        repository.search(null, query, null, tagFilter, pageable), mapper::toResponse);
  }

  public TestCaseResponse get(UUID id) {
    return mapper.toResponse(require(id));
  }

  /**
   * The one throw site for a missing case. Package-private and returning the entity: it is for the
   * other services in this feature, never for the web layer.
   */
  TestCase require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.testcase", id));
  }

  @Transactional
  public TestCaseResponse create(UUID projectId, TestCaseRequest request) {
    TestCase testCase = new TestCase();
    testCase.setWorkspaceId(projects.workspaceOf(projectId));
    testCase.setProjectId(projectId);
    testCase.setReference(projects.nextTestCaseReference(projectId));
    apply(testCase, request);
    return mapper.toResponse(repository.save(testCase));
  }

  @Transactional
  public TestCaseResponse update(UUID id, TestCaseRequest request) {
    TestCase testCase = require(id);
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
    repository.delete(require(id));
    events.publishEvent(new TestCaseEvents.TestCaseDeleted(id));
  }

  /** Records that the case's current text is in the vector store; {@link TestCaseIndexService}. */
  @Transactional
  TestCaseResponse markIndexed(UUID id) {
    TestCase testCase = require(id);
    testCase.setIndexedAt(Instant.now());
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
      step.setExpectedText(trimToNull(request.expected()));
      steps.add(step);
    }
    return steps;
  }

  private static String trimToNull(String text) {
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
