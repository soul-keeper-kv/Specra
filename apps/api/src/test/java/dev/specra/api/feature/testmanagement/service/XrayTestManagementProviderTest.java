package dev.specra.api.feature.testmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testmanagement.dto.ExternalTestDetail;
import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.service.TestManagementProvider.ProviderConnection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins how a Jira/Xray refusal is classified. The distinction matters to the caller: a 404 they can
 * fix by correcting the key, a 502 they can only report.
 */
class XrayTestManagementProviderTest {

  private static final String PROJECT = "XRAY";
  private static final String ISSUE_PATH = "/rest/api/2/issue/XRAY-1810";
  private static final String STEP_PATH = "/rest/raven/2.0/api/test/XRAY-1810/steps";

  private HttpServer server;
  private XrayTestManagementProvider provider;
  private ProviderConnection connection;

  /** path -> [status, body]; anything not registered answers 404, exactly as Jira would. */
  private final Map<String, Object[]> routes = new ConcurrentHashMap<>();

  /** The decoded query string of the most recent request, so the JQL a search built can be read. */
  private final AtomicReference<String> lastQuery = new AtomicReference<>("");

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastQuery.set(
              URLDecoder.decode(
                  exchange.getRequestURI().getRawQuery() == null
                      ? ""
                      : exchange.getRequestURI().getRawQuery(),
                  StandardCharsets.UTF_8));
          Object[] route = routes.get(exchange.getRequestURI().getPath());
          int status = route == null ? 404 : (int) route[0];
          byte[] body =
              ((String) (route == null ? "{}" : route[1])).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    provider = new XrayTestManagementProvider(new ObjectMapper());
    connection =
        new ProviderConnection(
            Map.of(
                XrayTestManagementProvider.BASE_URL,
                "http://127.0.0.1:" + server.getAddress().getPort()),
            Map.of(XrayTestManagementProvider.TOKEN, "pat"));
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void route(String path, int status, String body) {
    routes.put(path, new Object[] {status, body});
  }

  private static String issue(String projectKey) {
    return """
    {"key":"XRAY-1810","fields":{"summary":"Login","labels":[],\
    "priority":{"name":"High"},"project":{"key":"%s"}}}\
    """
        .formatted(projectKey);
  }

  /** What Xray Server 2.0 answers with: nested fields, and Action wrapped again as value.raw. */
  private static String steps() {
    return """
    {"steps":[{"id":1,"index":1,"fields":{\
    "Action":{"type":"Wiki","value":{"raw":"Open the login page","rendered":"<p>x</p>"}},\
    "Input Data":{"type":"Data","value":"/login"},\
    "Output Data":{"type":"Data","value":"The form shows"}}}]}\
    """;
  }

  @Test
  void readsATestThatExistsInTheBoundProject() {
    route(ISSUE_PATH, 200, issue(PROJECT));
    // The shape Xray Server 2.0 really returns: nested fields, a wiki wrapper on Action.
    route(STEP_PATH, 200, steps());

    ExternalTestDetail detail = provider.test(connection, PROJECT, "XRAY-1810");

    assertThat(detail.externalId()).isEqualTo("XRAY-1810");
    assertThat(detail.title()).isEqualTo("Login");
    assertThat(detail.steps())
        .singleElement()
        .satisfies(
            step -> {
              assertThat(step.position()).isEqualTo(1);
              assertThat(step.action()).isEqualTo("Open the login page");
              assertThat(step.data()).isEqualTo("/login");
              assertThat(step.expected()).isEqualTo("The form shows");
            });
  }

  @Test
  void reportsAnUnknownTestKeyAsNotFoundRatherThanAProviderError() {
    // Nothing registered: Jira answers 404, which is the caller's mistake, not a broken upstream.
    assertThatThrownBy(() -> provider.test(connection, PROJECT, "XRAY-1810"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXTERNAL_TEST_NOT_FOUND);
              assertThat(ex.errorCode().status().value()).isEqualTo(404);
              assertThat(ex.messageArgs()).containsExactly("XRAY-1810");
            });
  }

  @Test
  void importsATestXrayHoldsNoStepsForInsteadOfCallingItMissing() {
    // The issue is real and in the bound project; only the Xray step endpoint 404s, which is what
    // a Cucumber test, a non-Test issue type, or a Test with no steps yet all look like.
    route(ISSUE_PATH, 200, issue(PROJECT));

    ExternalTestDetail detail = provider.test(connection, PROJECT, "XRAY-1810");

    assertThat(detail.externalId()).isEqualTo("XRAY-1810");
    assertThat(detail.title()).isEqualTo("Login");
    assertThat(detail.steps()).isEmpty();
  }

  @Test
  void stillFailsWhenTheStepEndpointBreaksForAnyOtherReason() {
    route(ISSUE_PATH, 200, issue(PROJECT));
    route(STEP_PATH, 500, "{}");

    assertThatThrownBy(() -> provider.test(connection, PROJECT, "XRAY-1810"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTEGRATION_PROVIDER_ERROR));
  }

  @Test
  void reportsATestFromAnotherProjectAsNotFound() {
    route(ISSUE_PATH, 200, issue("OTHER"));

    assertThatThrownBy(() -> provider.test(connection, PROJECT, "XRAY-1810"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.EXTERNAL_TEST_NOT_FOUND));
  }

  @Test
  void stillReportsARefusedTokenAsAnAuthFailure() {
    route(ISSUE_PATH, 401, "{}");

    assertThatThrownBy(() -> provider.test(connection, PROJECT, "XRAY-1810"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTEGRATION_AUTH_FAILED));
  }

  @Test
  void stillReportsAServerSideFailureAsAProviderError() {
    route(ISSUE_PATH, 500, "{}");

    assertThatThrownBy(() -> provider.test(connection, PROJECT, "XRAY-1810"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTEGRATION_PROVIDER_ERROR);
              assertThat(ex.errorCode().status().value()).isEqualTo(502);
            });
  }

  @Test
  void aMissingProjectDuringVerifyStaysAProviderErrorNotATestLookup() {
    // No externalId is in play here, so a 404 means the base URL or the project key is wrong.
    route(
        "/rest/api/2/myself",
        200,
        """
        {"displayName":"QA"}\
        """);

    assertThatThrownBy(() -> provider.verify(connection, PROJECT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTEGRATION_PROVIDER_ERROR));
  }

  private static final String SEARCH_PATH = "/rest/api/2/search";

  private static String searchResult(long total) {
    return """
    {"total":%d,"issues":[{"key":"XRAY-1810","fields":{"summary":"Login",    "status":{"name":"Ready"},"priority":{"name":"High"},"labels":["smoke"]}}]}\
    """
        .formatted(total);
  }

  @Test
  void searchMatchesTheSummaryAndTheDescription() {
    route(SEARCH_PATH, 200, searchResult(1));

    provider.tests(connection, PROJECT, "login form", 0, 20);

    assertThat(lastQuery.get())
        .contains("project = \"XRAY\" AND issuetype = Test")
        .contains("summary ~ \"login form\"")
        .contains("description ~ \"login form\"");
  }

  @Test
  void searchAlsoMatchesTheKeyWhenTheTermLooksLikeOne() {
    // A QA pastes the key from a ticket. Matching only the summary would answer "no such test".
    route(SEARCH_PATH, 200, searchResult(1));

    provider.tests(connection, PROJECT, "xray-1810", 0, 20);

    assertThat(lastQuery.get()).contains("key = \"XRAY-1810\"");
  }

  @Test
  void searchLeavesTheKeyClauseOutForAnOrdinaryWord() {
    // "key = login" is not valid JQL; adding it unconditionally would fail every text search.
    route(SEARCH_PATH, 200, searchResult(1));

    provider.tests(connection, PROJECT, "login", 0, 20);

    assertThat(lastQuery.get()).doesNotContain("key = ");
  }

  @Test
  void searchPagesWithTheProvidersTotalSoTheCallerKnowsThereIsMore() {
    route(SEARCH_PATH, 200, searchResult(137));

    PageResponse<ExternalTestSummary> page = provider.tests(connection, PROJECT, null, 2, 20);

    assertThat(lastQuery.get()).contains("startAt=40").contains("maxResults=20");
    assertThat(page.totalElements()).isEqualTo(137);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.first()).isFalse();
    assertThat(page.last()).isFalse();
    assertThat(page.content())
        .singleElement()
        .extracting(ExternalTestSummary::externalId)
        .isEqualTo("XRAY-1810");
  }

  @Test
  void aQuotedTermCannotBreakOutOfTheJqlStringItIsPlacedIn() {
    route(SEARCH_PATH, 200, searchResult(0));

    provider.tests(connection, PROJECT, "a\" OR project = OTHER", 0, 20);

    assertThat(lastQuery.get()).contains("summary ~ \"a\\\" OR project = OTHER\"");
  }
}
