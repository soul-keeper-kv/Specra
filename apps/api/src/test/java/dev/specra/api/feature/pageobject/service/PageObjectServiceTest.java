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
import dev.specra.api.core.runner.RunnerClient;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.environment.service.EnvironmentService;
import dev.specra.api.feature.git.service.GitService;
import dev.specra.api.feature.pageobject.domain.LocatorStrategy;
import dev.specra.api.feature.pageobject.domain.PageObject;
import dev.specra.api.feature.pageobject.domain.PageObjectRepository;
import dev.specra.api.feature.pageobject.dto.InspectRequest;
import dev.specra.api.feature.pageobject.dto.PageObjectResponse;
import dev.specra.api.feature.project.service.ProjectService;
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

  // ── fixtures ────────────────────────────────────────────────────────────────

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
