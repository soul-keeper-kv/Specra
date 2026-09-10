package dev.specra.api.feature.pageobject.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.environment.service.EnvironmentService;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.pageobject.domain.LocatorStrategy;
import dev.specra.api.feature.pageobject.domain.PageElement;
import dev.specra.api.feature.pageobject.domain.PageObject;
import dev.specra.api.feature.pageobject.domain.PageObjectRepository;
import dev.specra.api.feature.pageobject.dto.InspectRequest;
import dev.specra.api.feature.pageobject.dto.PageElementResponse;
import dev.specra.api.feature.pageobject.dto.PageObjectResponse;
import dev.specra.api.feature.project.service.ProjectService;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.util.StringUtils;

/**
 * Page objects: what the application's pages contain, and how to reach it.
 *
 * <p>The row this feature owns is the one invariant 5 turns on. A locator that was *read* off a
 * real page is worth more than any a model could produce, so this service exists to make sure
 * nobody has to guess — and to make correcting a bad reading one edit rather than a regeneration.
 *
 * <p>Not {@code @Transactional} at class level: inspection is an HTTP call to the runner that
 * drives a browser, and holding a database connection for it would tie up the pool for the length
 * of a page load.
 */
@Service
public class PageObjectService {

  private static final Logger log = LoggerFactory.getLogger(PageObjectService.class);

  private final PageObjectRepository repository;
  private final ProjectService projects;
  private final EnvironmentService environments;
  private final GitService git;
  private final RunnerClient runner;

  public PageObjectService(
      PageObjectRepository repository,
      ProjectService projects,
      EnvironmentService environments,
      GitService git,
      RunnerClient runner) {
    this.repository = repository;
    this.projects = projects;
    this.environments = environments;
    this.git = git;
    this.runner = runner;
  }

  @Transactional(readOnly = true)
  public List<PageObjectResponse> list(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return repository.findByProjectIdOrderByNameAsc(projectId).stream()
        .map(PageObjectService::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public PageObjectResponse get(UUID id) {
    PageObject page = require(id);
    projects.requireAccess(page.getProjectId(), Permission.CONTENT_VIEW);
    return toResponse(page);
  }

  /**
   * Opens the page and records what it found.
   *
   * <p>Re-inspecting **replaces** the elements rather than merging them. A merge would keep a
   * locator for an element the page no longer has, and a page object that quietly accumulates dead
   * entries is how "the tests were passing yesterday" becomes unanswerable.
   */
  public PageObjectResponse inspect(UUID projectId, InspectRequest request) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);

    UUID environmentId =
        request.environmentId() != null
            ? environments.requireAccessible(request.environmentId(), projectId).id()
            : environments.defaultEnvironmentId(projectId);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("url", url(environments.baseUrlOf(environmentId), request.route()));
    payload.put("pageName", request.pageName().trim());
    // The engine lives in the user's project, so the runner needs the working copy to resolve it
    // — the same arrangement execution uses, and the reason the runner has no engine dependency.
    payload.put("projectDir", git.materialise(projectId).path());

    RunnerClient.RunnerJobResult job = runner.run("inspect", payload);
    if (!job.ok()) {
      // A page that will not open is the user's to fix — a wrong route, a site that is down. It
      // reaches them as a message rather than a stack trace.
      log.info("Inspection refused for {}: {}", request.pageName(), job.message());
      throw new ConflictException("error.inspection.failed", job.message());
    }

    // The runner reads a page and reports; whether a page it did not ask for is usable is a
    // decision, and decisions live here. Storing it would name a login form after the screen
    // behind it, and nothing downstream could tell.
    requireTheRequestedPage(job.result());

    return store(projectId, request, job.result());
  }

  /**
   * Refuses a reading taken from a page nobody asked for.
   *
   * <p>The runner reports where it ended up; this decides that landing somewhere else means the
   * inspection failed. The overwhelmingly common cause is an application redirecting an anonymous
   * visitor to its sign-in screen, and the elements read there belong to that screen — storing them
   * under the requested page's name is a wrong nothing downstream could detect.
   */
  private static void requireTheRequestedPage(Map<String, Object> result) {
    if (!(result.get("redirectedTo") instanceof Map<?, ?> redirect)) {
      return;
    }
    String requested = String.valueOf(redirect.get("requested"));
    String reached = String.valueOf(redirect.get("reached"));
    log.info("Inspection of {} was redirected to {}", requested, reached);
    throw new InspectionRedirectedException(requested, reached);
  }

  @Transactional
  PageObjectResponse store(UUID projectId, InspectRequest request, Map<String, Object> result) {
    String name = request.pageName().trim();
    PageObject page =
        repository
            .findByProjectIdAndName(projectId, name)
            .orElseGet(
                () -> {
                  PageObject created = new PageObject();
                  created.setWorkspaceId(projects.workspaceOf(projectId));
                  created.setProjectId(projectId);
                  created.setName(name);
                  return created;
                });

    page.setRoute(request.route());
    page.setInspectedAt(Instant.now());
    page.replaceElements(elementsOf(result));

    return toResponse(repository.save(page));
  }

  /**
   * The candidates the planner ranked become a locator and its fallback.
   *
   * <p>The choice is made here rather than in the runner because it is a decision, and decisions
   * belong above the component that runs untrusted pages. Taking [0] and [1] is only the default: a
   * person can pick differently, which is what {@code PUT …/elements/{name}} is for.
   */
  private static List<PageElement> elementsOf(Map<String, Object> result) {
    List<PageElement> elements = new ArrayList<>();
    for (Map<String, Object> raw : listOf(result.get("elements"))) {
      List<Map<String, Object>> candidates = listOf(raw.get("candidates"));
      if (candidates.isEmpty()) {
        // The runner already reports these as ambiguous; storing an element with no way to reach
        // it would put a getter in the page object that could never resolve.
        continue;
      }

      Map<String, Object> best = candidates.get(0);
      PageElement element = new PageElement();
      element.setName(String.valueOf(raw.get("name")));
      element.setStrategy(LocatorStrategy.fromCode(String.valueOf(best.get("strategy"))));
      element.setValue(String.valueOf(best.get("value")));
      element.setQualifier(best.get("name") == null ? null : String.valueOf(best.get("name")));
      element.setConfidence(confidenceOf(best.get("score")));

      if (candidates.size() > 1) {
        Map<String, Object> runnerUp = candidates.get(1);
        element.setFallbackStrategy(
            LocatorStrategy.fromCode(String.valueOf(runnerUp.get("strategy"))));
        element.setFallbackValue(String.valueOf(runnerUp.get("value")));
      }
      elements.add(element);
    }
    return elements;
  }

  /**
   * Corrects one element by hand.
   *
   * <p>The counterpart of {@code PUT …/model}: a reviewer shown a wrong locator and offered only
   * "inspect again" is a spectator. Inspection is a good guess about a real page, not an oracle,
   * and the person who knows the application must be able to overrule it.
   */
  @Transactional
  public PageObjectResponse updateElement(
      UUID pageObjectId,
      String elementName,
      LocatorStrategy strategy,
      String value,
      String qualifier) {
    PageObject page = require(pageObjectId);
    projects.requireAccess(page.getProjectId(), Permission.CONTENT_EDIT);

    PageElement element =
        page.getElements().stream()
            .filter(candidate -> candidate.getName().equals(elementName))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("resource.page-element", elementName));

    element.setStrategy(strategy);
    element.setValue(value);
    element.setQualifier(StringUtils.hasText(qualifier) ? qualifier : null);
    // A hand-written locator is certain by definition: a person looked at the page. Leaving the
    // planner's score would have the UI warn about a locator its author just confirmed.
    element.setConfidence(BigDecimal.ONE);

    return toResponse(repository.save(page));
  }

  /**
   * The pages a generation should be given, in the shape the runner's adapter expects.
   *
   * <p>Public because {@code feature/codegen} calls it. It returns only what has been inspected: a
   * page nobody has looked at contributes no elements, the runner reports its targets as
   * unresolved, and the proposal says "inspect these pages first" instead of shipping a guess.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> forGeneration(UUID projectId, List<String> pageNames) {
    List<Map<String, Object>> pages = new ArrayList<>();
    for (PageObject page : repository.findByProjectIdOrderByNameAsc(projectId)) {
      if (!pageNames.contains(page.getName()) || page.getInspectedAt() == null) {
        continue;
      }
      List<Map<String, Object>> elements = new ArrayList<>();
      for (PageElement element : page.getElements()) {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("name", element.getName());
        projected.put("strategy", element.getStrategy().code());
        projected.put("value", element.getValue());
        if (StringUtils.hasText(element.getQualifier())) {
          // The adapter's field for a role's accessible name. Named `name_` there because
          // `name` is already the element's identifier.
          projected.put("name_", element.getQualifier());
        }
        if (element.getFallbackStrategy() != null) {
          projected.put(
              "fallback",
              Map.of(
                  "strategy", element.getFallbackStrategy().code(),
                  "value", element.getFallbackValue()));
        }
        if (element.getConfidence() != null) {
          projected.put("confidence", element.getConfidence().doubleValue());
        }
        elements.add(projected);
      }

      Map<String, Object> projected = new LinkedHashMap<>();
      projected.put("name", page.getName());
      if (StringUtils.hasText(page.getRoute())) {
        projected.put("route", page.getRoute());
      }
      projected.put("elements", elements);
      pages.add(projected);
    }
    return pages;
  }

  private PageObject require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.page-object", id));
  }

  /** Base URL and route, with exactly one slash between them. */
  static String url(String baseUrl, String route) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String path = route.startsWith("/") ? route : "/" + route;
    return base + path;
  }

  private static BigDecimal confidenceOf(Object score) {
    if (!(score instanceof Number number)) {
      return null;
    }
    return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOf(Object value) {
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private static PageObjectResponse toResponse(PageObject page) {
    List<PageElementResponse> elements =
        page.getElements().stream()
            .map(
                element ->
                    new PageElementResponse(
                        element.getId(),
                        element.getName(),
                        element.getStrategy(),
                        element.getValue(),
                        element.getQualifier(),
                        element.getFallbackStrategy(),
                        element.getFallbackValue(),
                        element.getConfidence()))
            .toList();

    return new PageObjectResponse(
        page.getId(),
        page.getProjectId(),
        page.getName(),
        page.getRoute(),
        page.getInspectedAt(),
        elements,
        page.getUpdatedAt());
  }
}
