package dev.specra.api.feature.testmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ErrorCode;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Jira Data Center/Xray implementation. All Jira vocabulary is contained in this adapter. */
@Component
public class XrayTestManagementProvider implements TestManagementProvider {
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
    JsonNode identity = get(connection, "/rest/api/2/myself");
    JsonNode project = get(connection, "/rest/api/2/project/" + encode(remoteProjectId));
    long count = search(connection, remoteProjectId, null, 0, 1).path("total").asLong();
    return new TestManagementVerifyResponse(
        identity.path("displayName").asText(identity.path("name").asText()),
        project.path("key").asText(remoteProjectId),
        project.path("name").asText(),
        count);
  }

  @Override
  public List<ExternalTestSummary> tests(
      ProviderConnection connection, String remoteProjectId, String query, int page, int size) {
    JsonNode node = search(connection, remoteProjectId, query, page * size, size);
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
    return List.copyOf(tests);
  }

  private JsonNode search(
      ProviderConnection connection,
      String remoteProjectId,
      String query,
      int startAt,
      int maxResults) {
    StringBuilder jql =
        new StringBuilder("project = \"")
            .append(escapeJql(remoteProjectId))
            .append("\" AND issuetype = Test");
    if (StringUtils.hasText(query)) {
      jql.append(" AND summary ~ \"").append(escapeJql(query.trim())).append("\"");
    }
    return get(
        connection,
        "/rest/api/2/search?jql="
            + encode(jql.toString())
            + "&startAt="
            + startAt
            + "&maxResults="
            + maxResults
            + "&fields=summary%2Cstatus%2Cpriority%2Clabels");
  }

  private JsonNode get(ProviderConnection connection, String path) {
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
        throw remoteError(response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TestManagementRemoteException(ErrorCode.INTEGRATION_UNAVAILABLE);
    } catch (IOException e) {
      throw new TestManagementRemoteException(ErrorCode.INTEGRATION_UNAVAILABLE);
    }
  }

  private static String baseUrl(ProviderConnection connection) {
    return connection.configuration().get(BASE_URL).trim().replaceAll("/+$", "");
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

  private static String escapeJql(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static TestManagementRemoteException remoteError(int status) {
    return new TestManagementRemoteException(
        status == 401 || status == 403
            ? ErrorCode.INTEGRATION_AUTH_FAILED
            : ErrorCode.INTEGRATION_PROVIDER_ERROR);
  }
}
