package dev.specra.api.feature.pageobject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What inspection stores, and what it refuses to store.
 *
 * <p>The scoring itself lives in the runner and is tested there; these cover the decisions this
 * side owns — which candidate becomes the locator, what happens to one that has none, and that a
 * page nobody has looked at contributes nothing to a generation.
 */
@ExtendWith(MockitoExtension.class)
class PageObjectServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID ENVIRONMENT = UUID.randomUUID();

  @Mock PageObjectRepository repository;
  @Mock ProjectService projects;
  @Mock EnvironmentService environments;
  @Mock GitService git;
  @Mock RunnerClient runner;

  PageObjectService service;

  @BeforeEach
  void setUp() {
    service = new PageObjectService(repository, projects, environments, git, runner);
    lenient().when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    lenient().when(environments.defaultEnvironmentId(PROJECT)).thenReturn(ENVIRONMENT);
    lenient().when(environments.baseUrlOf(ENVIRONMENT)).thenReturn("https://staging.acme.dev");
    lenient()
        .when(git.materialise(PROJECT))
        .thenReturn(new GitService.WorkingCopyAt("/tmp/copy", "abc1234", null));
    lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    lenient()
        .when(repository.findByProjectIdAndName(eq(PROJECT), any()))
        .thenReturn(Optional.empty());
  }

  @Test
  void theBestCandidateBecomesTheLocatorAndTheRunnerUpTheFallback() {
    when(runner.run(eq("inspect"), any())).thenReturn(inspected());

    PageObjectResponse page = service.inspect(PROJECT, request());

    verify(projects).requireAccess(PROJECT, Permission.CONTENT_EDIT);
    assertThat(page.elements())
        .singleElement()
        .satisfies(
            element -> {
              assertThat(element.name()).isEqualTo("submitButton");
              assertThat(element.strategy()).isEqualTo(LocatorStrategy.TEST_ID);
              assertThat(element.value()).isEqualTo("submit");
              // The runner-up is what failure analysis proposes first when the primary drifts.
              assertThat(element.fallbackStrategy()).isEqualTo(LocatorStrategy.ROLE);
              assertThat(element.fallbackValue()).isEqualTo("button");
              assertThat(element.confidence()).isEqualByComparingTo("1.00");
            });
  }

  /** A role is only unique with its accessible name, and the two are separate arguments. */
  @Test
  void aRoleLocatorKeepsItsAccessibleName() {
    when(runner.run(eq("inspect"), any()))
        .thenReturn(
            RunnerClient.RunnerJobResult.succeeded(
                Map.of(
                    "elements",
                    List.of(
                        Map.of(
                            "name",
                            "signInButton",
                            "candidates",
                            List.of(
                                Map.of(
                                    "strategy", "role",
                                    "value", "button",
                                    "name", "Sign in",
                                    "score", 0.9)))))));

    PageObjectResponse page = service.inspect(PROJECT, request());

    assertThat(page.elements().get(0).qualifier()).isEqualTo("Sign in");
  }

  /**
   * An element the runner could not address uniquely would become a page-object getter that can
   * never resolve — worse than its absence, which at least shows up as an unresolved target.
   */
  @Test
  void anElementWithNoUsableCandidateIsNotStored() {
    when(runner.run(eq("inspect"), any()))
        .thenReturn(
            RunnerClient.RunnerJobResult.succeeded(
                Map.of(
                    "elements", List.of(Map.of("name", "deleteButton", "candidates", List.of())))));

    assertThat(service.inspect(PROJECT, request()).elements()).isEmpty();
  }

  /** A page that will not open is the user's to fix, and reaches them as a message. */
  @Test
  void aRefusedInspectionIsReportedRatherThanStored() {
    when(runner.run(eq("inspect"), any()))
        .thenReturn(RunnerClient.RunnerJobResult.refused("generation-failed", "net::ERR_REFUSED"));

    assertThatThrownBy(() -> service.inspect(PROJECT, request()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("inspection.failed");

    verify(repository, never()).save(any());
  }

  /** The runner is handed an absolute URL: it knows nothing about environments. */
  @Test
  void theRouteIsJoinedToTheEnvironmentsBaseUrl() {
    when(runner.run(eq("inspect"), any())).thenReturn(inspected());

    service.inspect(PROJECT, request());

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(runner).run(eq("inspect"), payload.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> sent = (Map<String, Object>) payload.getValue();
    assertThat(sent.get("url")).isEqualTo("https://staging.acme.dev/login");
    // The engine lives in the user's project, so the runner needs the working copy to find it.
    assertThat(sent.get("projectDir")).isEqualTo("/tmp/copy");
  }

  @Test
  void joiningNeverProducesADoubleSlash() {
    assertThat(PageObjectService.url("https://a.dev/", "/login")).isEqualTo("https://a.dev/login");
    assertThat(PageObjectService.url("https://a.dev", "login")).isEqualTo("https://a.dev/login");
    assertThat(PageObjectService.url("https://a.dev", "/login")).isEqualTo("https://a.dev/login");
  }

  /**
   * The absence that makes invariant 5 work: a generation is given nothing for a page nobody has
   * looked at, so the runner reports its targets as unresolved instead of guessing a selector.
   */
  @Test
  void anUninspectedPageContributesNothingToAGeneration() {
    PageObject never = new PageObject();
    never.setProjectId(PROJECT);
    never.setName("LoginPage");
    never.setInspectedAt(null);
    when(repository.findByProjectIdOrderByNameAsc(PROJECT)).thenReturn(List.of(never));

    assertThat(service.forGeneration(PROJECT, List.of("LoginPage"))).isEmpty();
  }

  // ── a page that was never reached ───────────────────────────────────────────

  /**
   * The silent wrong this refusal exists to prevent.
   *
   * <p>An application that bounces an anonymous visitor to its sign-in screen used to produce a
   * {@code DashboardPage} holding the login form's elements, with no error anywhere. Everything
   * downstream believed it, and nobody could tell by looking.
   */
  @Test
  void aPageThatRedirectedIsRefusedRatherThanStored() {
    when(runner.run(eq("inspect"), any()))
        .thenReturn(
            RunnerClient.RunnerJobResult.succeeded(
                Map.of(
                    "elements",
                    List.of(),
                    "redirectedTo",
                    Map.of(
                        "requested", "https://staging.acme.dev/dashboard",
                        "reached", "https://staging.acme.dev/login"))));

    assertThatThrownBy(() -> service.inspect(PROJECT, request()))
        .isInstanceOf(InspectionRedirectedException.class)
        .satisfies(
            thrown -> {
              InspectionRedirectedException redirected = (InspectionRedirectedException) thrown;
              // Both URLs reach the client, so the UI can say where it landed rather than only
              // that something went wrong.
              assertThat(redirected.extensions())
                  .containsEntry("reached", "https://staging.acme.dev/login");
              assertThat(redirected.errorCode()).isEqualTo(ErrorCode.INSPECTION_REDIRECTED);
            });

    verify(repository, never()).save(any());
  }

  /** The ordinary case must not pay for the check: no redirect reported, nothing refused. */
  @Test
  void aPageThatWasReachedIsStored() {
    when(runner.run(eq("inspect"), any())).thenReturn(inspected());

    assertThat(service.inspect(PROJECT, request()).elements()).hasSize(1);
  }

  // ── re-inspection ───────────────────────────────────────────────────────────

  /**
   * The case every test above missed by starting from a page that did not exist yet.
   *
   * <p>An element that is still on the page must keep its row. Clearing the collection and adding
   * the same name back made Hibernate insert it while the old row was still there, and {@code
   * uq_page_elements_name} refused — so the second inspection of any page failed with a 409 that
   * said the resource had changed.
   */
  @Test
  void reInspectingAPageKeepsTheRowAndTakesTheNewLocator() {
    PageObject existing = inspectedEarlier();
    PageElement before = existing.getElements().get(0);
    when(repository.findByProjectIdAndName(PROJECT, "LoginPage")).thenReturn(Optional.of(existing));
    when(runner.run(eq("inspect"), any())).thenReturn(inspected());

    service.inspect(PROJECT, request());

    // The same instance, so there is no INSERT to collide with the row already on the table.
    assertThat(existing.getElements()).singleElement().isSameAs(before);
    // The UI moved and inspection read where it moved to; that is what a re-inspection is for.
    assertThat(before.getStrategy()).isEqualTo(LocatorStrategy.TEST_ID);
    assertThat(before.getValue()).isEqualTo("submit");
  }

  /** A page object that kept locators for elements the page no longer has would rot silently. */
  @Test
  void anElementThePageNoLongerHasIsDropped() {
    PageObject existing = inspectedEarlier();
    PageElement removed = new PageElement();
    removed.setName("legacyButton");
    removed.setStrategy(LocatorStrategy.CSS);
    removed.setValue("#legacy");
    removed.setPageObject(existing);
    existing.getElements().add(removed);
    when(repository.findByProjectIdAndName(PROJECT, "LoginPage")).thenReturn(Optional.of(existing));
    when(runner.run(eq("inspect"), any())).thenReturn(inspected());

    PageObjectResponse page = service.inspect(PROJECT, request());

    assertThat(page.elements())
        .extracting(PageElementResponse::name)
        .containsExactly("submitButton");
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  /** A page inspected once already, holding the same element under an older locator. */
  private static PageObject inspectedEarlier() {
    PageObject page = new PageObject();
    page.setProjectId(PROJECT);
    page.setWorkspaceId(WORKSPACE);
    page.setName("LoginPage");
    page.setRoute("/login");
    page.setInspectedAt(Instant.now());

    PageElement element = new PageElement();
    element.setName("submitButton");
    element.setStrategy(LocatorStrategy.CSS);
    element.setValue("#old-submit");
    element.setPageObject(page);
    page.getElements().add(element);
    return page;
  }

  private static InspectRequest request() {
    return new InspectRequest("LoginPage", "/login", null);
  }

  private static RunnerClient.RunnerJobResult inspected() {
    return RunnerClient.RunnerJobResult.succeeded(
        Map.of(
            "elements",
            List.of(
                Map.of(
                    "name",
                    "submitButton",
                    "candidates",
                    List.of(
                        Map.of("strategy", "testId", "value", "submit", "score", 1.0),
                        Map.of("strategy", "role", "value", "button", "score", 0.9))))));
  }
}
