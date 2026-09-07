package dev.specra.api.feature.testmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testmanagement.dto.ExternalTestDetail;
import dev.specra.api.feature.testmanagement.dto.ExternalTestStep;
import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.dto.TestManagementVerifyResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Jira Data Center/Xray implementation. All Jira vocabulary is contained in this adapter. */
@Component
public class XrayTestManagementProvider implements TestManagementProvider {
  private static final Logger log = LoggerFactory.getLogger(XrayTestManagementProvider.class);

  /** A Jira key, so "key = …" is only added to the JQL when the term could actually be one. */
  private static final Pattern ISSUE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*-[0-9]+");

  static final String BASE_URL = "baseUrl";
  static final String TOKEN = "token";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public XrayTestManagementProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  @Override
  public String kind() {
    return "xray";
  }

  @Override
  public void validate(Map<String, String> configuration, Map<String, String> credentials) {
    String baseUrl = configuration.get(BASE_URL);
    if (!StringUtils.hasText(baseUrl) || !validHttpUrl(baseUrl)) {
      throw new ConflictException("error.test-management.xray.invalid-url");
    }
    if (!StringUtils.hasText(credentials.get(TOKEN))) {
      throw new ConflictException("error.test-management.xray.token-required");
    }
  }

  @Override
  public TestManagementVerifyResponse verify(
      ProviderConnection connection, String remoteProjectId) {
    JsonNode identity = get(connection, "/rest/api/2/myself", null);
    JsonNode project = get(connection, "/rest/api/2/project/" + encode(remoteProjectId), null);
    long count = search(connection, remoteProjectId, null, false, 0, 1).path("total").asLong();
    return new TestManagementVerifyResponse(
        identity.path("displayName").asText(identity.path("name").asText()),
        project.path("key").asText(remoteProjectId),
        project.path("name").asText(),
        count);
  }

  @Override
  public PageResponse<ExternalTestSummary> tests(
      ProviderConnection connection,
      String remoteProjectId,
      String query,
      boolean advanced,
      int page,
      int size) {
    JsonNode node = search(connection, remoteProjectId, query, advanced, page * size, size);
    List<ExternalTestSummary> tests = new ArrayList<>();
    for (JsonNode issue : node.path("issues")) {
      JsonNode fields = issue.path("fields");
      List<String> labels = new ArrayList<>();
      fields.path("labels").forEach(label -> labels.add(label.asText()));
      String key = issue.path("key").asText();
      tests.add(
          new ExternalTestSummary(
              key,
              fields.path("summary").asText(),
              fields.path("status").path("name").asText(),
              fields.path("priority").path("name").asText(),
              List.copyOf(labels),
              baseUrl(connection) + "/browse/" + key));
    }
    return PageResponse.of(
        List.copyOf(tests), PageRequest.of(page, size), node.path("total").asLong());
  }

  @Override
  public ExternalTestDetail test(
      ProviderConnection connection, String remoteProjectId, String externalId) {
    JsonNode issue =
        get(
            connection,
            "/rest/api/2/issue/"
                + encode(externalId)
                + "?fields=summary%2Cdescription%2Cpriority%2Clabels%2Cproject",
            externalId);
    JsonNode fields = issue.path("fields");
    String actualProject = fields.path("project").path("key").asText();
    if (!remoteProjectId.equalsIgnoreCase(actualProject)) {
      // Jira knows the issue, but it belongs to a project this binding does not cover. Reported as
      // "not found" rather than "wrong project": which keys exist elsewhere in Jira is not
      // something a caller bound to one project should be able to probe for.
      log.info(
          "Xray test {} belongs to project {}, not the bound {}",
          externalId,
          actualProject,
          remoteProjectId);
      throw new TestManagementRemoteException(ErrorCode.EXTERNAL_TEST_NOT_FOUND, externalId);
    }

    List<ExternalTestStep> steps = new ArrayList<>();
    int position = 1;
    for (JsonNode step : steps(connection, externalId)) {
      JsonNode stepFields = step.path("fields");
      steps.add(
          new ExternalTestStep(
              position++,
              stepText(step, stepFields, "Action", "step", "action"),
              stepText(step, stepFields, "Input Data", "data"),
              stepText(step, stepFields, "Output Data", "result", "expectedResult")));
    }

    List<String> labels = new ArrayList<>();
    fields.path("labels").forEach(label -> labels.add(label.asText()));
    String key = issue.path("key").asText(externalId);
    return new ExternalTestDetail(
        key,
        fields.path("summary").asText(),
        nullableText(fields.path("description")),
        fields.path("priority").path("name").asText(),
        List.copyOf(labels),
        baseUrl(connection) + "/browse/" + key,
        List.copyOf(steps));
  }

  /**
   * The manual steps, or none.
   *
   * <p>Xray answers 404 here for an issue it does not hold steps for — a Cucumber or Generic test,
   * an issue type that is not Test, or simply a Test nobody has written steps into yet. None of
   * those mean the test is missing: the issue was already fetched and its project checked, so the
   * key is known to be good. A step list is optional in Specra too ({@code TestCaseRequest} calls
   * an empty one a valid draft), so this degrades to no steps instead of failing the whole read —
   * reporting "not found" for a key that plainly exists sends the user hunting for the wrong bug.
   */
  private JsonNode steps(ProviderConnection connection, String externalId) {
    try {
      JsonNode raw =
          get(connection, "/rest/raven/2.0/api/test/" + encode(externalId) + "/steps", externalId);
      return raw.isArray() ? raw : raw.path("steps");
    } catch (TestManagementRemoteException e) {
      if (e.errorCode() == ErrorCode.EXTERNAL_TEST_NOT_FOUND) {
        log.info("Xray holds no manual steps for {}; importing it without them", externalId);
        return MissingNode.getInstance();
      }
      throw e;
    }
  }

  private JsonNode search(
      ProviderConnection connection,
      String remoteProjectId,
      String query,
      boolean advanced,
      int startAt,
      int maxResults) {
    StringBuilder jql =
        new StringBuilder("project = \"")
            .append(escapeJql(remoteProjectId))
            .append("\" AND issuetype = Test");
    String ordering = " ORDER BY key ASC";
    if (advanced && StringUtils.hasText(query)) {
      String term = query.trim();
      int orderAt = orderByIndex(term);
      if (orderAt >= 0) {
        ordering = " " + term.substring(orderAt);
        term = term.substring(0, orderAt).trim();
      }
      if (!term.isEmpty()) {
        jql.append(" AND (").append(term).append(")");
      }
    } else if (StringUtils.hasText(query)) {
      // A QA searching an external system types either words from the title or the key they were
      // given in a ticket. Matching only the summary makes "XRAY-123" return nothing, which reads
      // as "the test is not there". "key = X" is only valid JQL for a well-formed key, so it is
      // added conditionally rather than always.
      String term = query.trim();
      jql.append(" AND (summary ~ \"").append(escapeJql(term)).append("\"");
      jql.append(" OR description ~ \"").append(escapeJql(term)).append("\"");
      if (ISSUE_KEY.matcher(term).matches()) {
        jql.append(" OR key = \"").append(escapeJql(term.toUpperCase(Locale.ROOT))).append("\"");
      }
      jql.append(")");
    }
    jql.append(ordering);
    return get(
        connection,
        "/rest/api/2/search?jql="
            + encode(jql.toString())
            + "&startAt="
            + startAt
            + "&maxResults="
            + maxResults
            + "&fields=summary%2Cstatus%2Cpriority%2Clabels",
        null);
  }

  /**
   * One authenticated GET against Jira.
   *
   * <p>{@code externalId} names the test being fetched, or is null when the call is not about one.
   * It decides how a 404 is reported: a missing test is the caller's 404, while a 404 on any other
   * path means the configured base URL or the Jira/Xray install is wrong, which is a 502.
   */
  private JsonNode get(ProviderConnection connection, String path, String externalId) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl(connection) + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + connection.credentials().get(TOKEN).trim())
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        // The status is the one fact that separates "wrong key" from "Xray is misconfigured",
        // and the problem document deliberately does not carry it — so it is logged here. The
        // path is safe to log; the token travels in a header and never appears in it.
        log.warn("Jira/Xray answered {} for {}", response.statusCode(), path);
        if (response.statusCode() == 400 && path.startsWith("/rest/api/2/search?")) {
          throw new TestManagementRemoteException(ErrorCode.INTEGRATION_QUERY_INVALID);
        }
        throw remoteError(response.statusCode(), externalId);
      }
      return objectMapper.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TestManagementRemoteException(ErrorCode.INTEGRATION_UNAVAILABLE);
    } catch (IOException e) {
      log.warn("Jira/Xray unreachable for {}: {}", path, e.toString());
      throw new TestManagementRemoteException(ErrorCode.INTEGRATION_UNAVAILABLE);
    }
  }

  private static String baseUrl(ProviderConnection connection) {
    return connection.configuration().get(BASE_URL).trim().replaceAll("/+$", "");
  }

  /**
   * One step field, from whichever shape this Xray answered with.
   *
   * <p>Xray 2.0 nests everything under {@code fields}, names them {@code Action} / {@code Input
   * Data} / {@code Output Data}, and wraps a wiki field again as {@code value.raw} with the
   * rendered HTML beside it — the raw text is what a test case wants, not the markup. Older and
   * flatter payloads put a plain string at the top level, so both are tried before giving up.
   */
  private static String stepText(
      JsonNode step, JsonNode fields, String xrayName, String... legacy) {
    JsonNode field = fields.path(xrayName);
    if (!field.isMissingNode()) {
      JsonNode value = field.path("value");
      String raw = nullableText(value.isObject() ? value.path("raw") : value);
      if (raw != null) {
        return raw;
      }
    }
    return text(step, legacy);
  }

  private static String text(JsonNode node, String... names) {
    for (String name : names) {
      String value = nullableText(node.path(name));
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private static String nullableText(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return StringUtils.hasText(value) ? value : null;
  }

  private static boolean validHttpUrl(String input) {
    try {
      URI uri = new URI(input.trim());
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /** Find sorting outside quoted values; Jira validates the complete expression. */
  private static int orderByIndex(String query) {
    Pattern orderBy = Pattern.compile("(?i)order\\s+by\\b");
    char quote = 0;
    int depth = 0;
    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (c == '\\') {
        i++;
      } else if (quote != 0) {
        if (c == quote) quote = 0;
      } else if (c == '\'' || c == '"') {
        quote = c;
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        if (--depth < 0) {
          throw new TestManagementRemoteException(ErrorCode.INTEGRATION_QUERY_INVALID);
        }
      } else if (depth == 0
          && (i == 0 || Character.isWhitespace(query.charAt(i - 1)))
          && orderBy.matcher(query.substring(i)).lookingAt()) {
        return i;
      }
    }
    if (quote != 0 || depth != 0) {
      throw new TestManagementRemoteException(ErrorCode.INTEGRATION_QUERY_INVALID);
    }
    return -1;
  }

  private static String escapeJql(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static TestManagementRemoteException remoteError(int status, String externalId) {
    if (status == 401 || status == 403) {
      return new TestManagementRemoteException(ErrorCode.INTEGRATION_AUTH_FAILED);
    }
    if (status == 404 && externalId != null) {
      return new TestManagementRemoteException(ErrorCode.EXTERNAL_TEST_NOT_FOUND, externalId);
    }
    return new TestManagementRemoteException(ErrorCode.INTEGRATION_PROVIDER_ERROR);
  }
}
