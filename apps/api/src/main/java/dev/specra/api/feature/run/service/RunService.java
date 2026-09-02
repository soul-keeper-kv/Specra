package dev.specra.api.feature.run.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.environment.service.EnvironmentService;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.domain.AutomationTest;
import dev.specra.api.feature.run.domain.AutomationTestRepository;
import dev.specra.api.feature.run.domain.ItemStatus;
import dev.specra.api.feature.run.domain.RunStatus;
import dev.specra.api.feature.run.domain.RunTrigger;
import dev.specra.api.feature.run.domain.TestRun;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.domain.TestRunItemRepository;
import dev.specra.api.feature.run.domain.TestRunRepository;
import dev.specra.api.feature.run.dto.RunRequest;
import dev.specra.api.feature.run.dto.RunResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requesting a run, and reading one back.
 *
 * <p>The request is short: it validates, mints a reference, writes one row per matrix cell in
 * {@code QUEUED}, and answers. Everything slow — materialising the working copy, installing,
 * driving a browser — happens on {@link RunExecutor} afterwards, which is why the endpoint can be a
 * 202. Holding an HTTP request open for the length of a test suite would tie a request thread to a
 * browser and give the user a spinner instead of a run they can navigate away from.
 *
 * <p>Concurrency is a per-workspace limit rather than a global one, so a workspace that queues
 * fifty runs slows itself down and nobody else.
 */
@Service
@Transactional(readOnly = true)
public class RunService {

  private final TestRunRepository runs;
  private final TestRunItemRepository items;
  private final AutomationTestRepository automationTests;
  private final ProjectService projects;
  private final EnvironmentService environments;
  private final GitService git;
  private final RunExecutor executor;
  private final RunViews views;
  private final int maxConcurrentPerWorkspace;

  public RunService(
      TestRunRepository runs,
      TestRunItemRepository items,
      AutomationTestRepository automationTests,
      ProjectService projects,
      EnvironmentService environments,
      GitService git,
      RunExecutor executor,
      RunViews views,
      dev.specra.api.config.SpecraProperties properties) {
    this.runs = runs;
    this.items = items;
    this.automationTests = automationTests;
    this.projects = projects;
    this.environments = environments;
    this.git = git;
    this.executor = executor;
    this.views = views;
    this.maxConcurrentPerWorkspace = properties.runner().maxConcurrentPerWorkspace();
  }

  public PageResponse<RunResponse> list(UUID projectId, Pageable pageable) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return PageResponse.from(
        runs.findByProjectIdOrderByQueuedAtDesc(projectId, pageable), views::toSummary);
  }

  public RunResponse get(UUID id) {
    TestRun run = require(id);
    projects.requireAccess(run.getProjectId(), Permission.CONTENT_VIEW);
    return views.toDetail(run);
  }

  /**
   * Queues a run and hands it back immediately.
   *
   * <p>The commit sha is resolved here rather than when the executor picks the run up, so the run
   * records the tree the user was looking at when they asked — not whatever the branch had moved to
   * by the time a worker was free.
   */
  @Transactional
  public RunResponse request(UUID projectId, RunRequest request) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);
    UUID workspaceId = projects.workspaceOf(projectId);

    if (runs.countByWorkspaceIdAndStatusIn(
            workspaceId, List.of(RunStatus.QUEUED, RunStatus.RUNNING))
        >= maxConcurrentPerWorkspace) {
      throw new ConflictException("error.run.too-many-in-flight", maxConcurrentPerWorkspace);
    }

    UUID environmentId =
        request.environmentId() != null
            ? request.environmentId()
            : environments.defaultEnvironmentId(projectId);
    // Checked here so a run against another project's environment is refused before anything is
    // written, rather than discovered when the executor reads a base URL it should not see.
    environments.requireAccessible(environmentId, projectId);

    List<AutomationTest> selected = select(projectId, request.testCaseIds());
    if (selected.isEmpty()) {
      throw new ConflictException("error.run.nothing-to-run");
    }

    GitService.WorkingCopyAt copy = git.materialise(projectId);

    TestRun run = new TestRun();
    run.setWorkspaceId(workspaceId);
    run.setProjectId(projectId);
    run.setReference(projects.nextTestRunReference(projectId));
    run.setEnvironmentId(environmentId);
    run.setCommitSha(copy.commitSha());
    run.setDirtyDiff(copy.dirtyDiff());
    run.setTrigger(RunTrigger.MANUAL);
    run.setStatus(RunStatus.QUEUED);
    run.setQueuedAt(Instant.now());
    run.setRequestedBy(CurrentUser.requireId());

    for (AutomationTest test : selected) {
      for (String browser : browsersOf(request)) {
        TestRunItem item = new TestRunItem();
        item.setAutomationTestId(test.getId());
        item.setTestCaseId(test.getTestCaseId());
        item.setSpecPath(test.getSpecPath());
        item.setBrowser(browser);
        item.setStatus(ItemStatus.QUEUED);
        run.addItem(item);
      }
    }

    TestRun saved = runs.save(run);
    // After the transaction commits, so the executor cannot read a run that is not there yet.
    executor.enqueue(saved.getId(), copy.path());
    return views.toDetail(saved);
  }

  /**
   * Marks a run cancelled.
   *
   * <p>A queued run stops before it starts. A running one is marked and the executor notices at its
   * next checkpoint — killing a browser mid-assertion would leave the working copy in a state
   * nobody chose, and the run is over either way.
   */
  @Transactional
  public RunResponse cancel(UUID id) {
    TestRun run = require(id);
    projects.requireAccess(run.getProjectId(), Permission.CONTENT_EDIT);
    if (run.getStatus().isTerminal()) {
      throw new ConflictException("error.run.already-finished", run.getReference());
    }
    run.setStatus(RunStatus.CANCELLED);
    run.setCompletedAt(Instant.now());
    return views.toDetail(runs.save(run));
  }

  private TestRun require(UUID id) {
    return runs.findById(id).orElseThrow(() -> new ResourceNotFoundException("resource.run", id));
  }

  /**
   * One matrix cell, for the endpoints addressed by item rather than by run.
   *
   * <p>Access is not checked here: the caller does it against the item's project, which is the only
   * place that knows what the caller is about to do with it.
   */
  public TestRunItem requireItem(UUID itemId) {
    return items
        .findById(itemId)
        .orElseThrow(() -> new ResourceNotFoundException("resource.run-item", itemId));
  }

  /**
   * The automation tests to execute: the ones named, or every one the project has.
   *
   * <p>A case with no automation test is silently absent rather than an error — asking to run five
   * cases of which three are automated should run three, not refuse. Selecting *none* is the
   * failure, and it is reported.
   */
  private List<AutomationTest> select(UUID projectId, List<UUID> testCaseIds) {
    if (testCaseIds == null || testCaseIds.isEmpty()) {
      return automationTests.findByProjectId(projectId);
    }
    List<AutomationTest> found = automationTests.findByTestCaseIdIn(testCaseIds);
    List<AutomationTest> inProject = new ArrayList<>();
    Set<UUID> allowed =
        new LinkedHashSet<>(
            automationTests.findByProjectId(projectId).stream()
                .map(AutomationTest::getId)
                .toList());
    for (AutomationTest test : found) {
      if (allowed.contains(test.getId())) {
        inProject.add(test);
      }
    }
    return inProject;
  }

  /** Chromium alone by default: it is the browser every install has, and the matrix is a choice. */
  private static List<String> browsersOf(RunRequest request) {
    if (request.browsers() == null || request.browsers().isEmpty()) {
      return List.of("chromium");
    }
    List<String> browsers = new ArrayList<>();
    for (String browser : new LinkedHashSet<>(request.browsers())) {
      if (!List.of("chromium", "firefox", "webkit").contains(browser)) {
        throw new BusinessException(
            ErrorCode.INVALID_PARAMETER, "error.run.unknown-browser", browser);
      }
      browsers.add(browser);
    }
    return browsers;
  }
}
