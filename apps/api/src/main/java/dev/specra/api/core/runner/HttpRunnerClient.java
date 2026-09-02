package dev.specra.api.core.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.config.SpecraProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one class that knows the runner is reached over HTTP.
 *
 * <p>The JDK's own client rather than a framework one: the surface is a single POST, and the
 * timeout is the only setting that matters — a codegen job is milliseconds, so a slow answer means
 * something is wrong rather than something is big.
 */
@Component
public class HttpRunnerClient implements RunnerClient {

  private static final Logger log = LoggerFactory.getLogger(HttpRunnerClient.class);

  private final HttpClient http;
  private final ObjectMapper json;
  private final URI jobs;
  private final URI health;
  private final Duration timeout;

  public HttpRunnerClient(SpecraProperties properties, ObjectMapper json) {
    SpecraProperties.Runner runner = properties.runner();
    String base = runner.baseUrl().replaceAll("/+$", "");
    this.jobs = URI.create(base + "/jobs");
    this.health = URI.create(base + "/health");
    this.timeout = runner.timeout();
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public RunnerJobResult run(String kind, Object payload) {
    String body;
    try {
      body = json.writeValueAsString(Map.of("kind", kind, "payload", payload));
    } catch (IOException e) {
      throw new IllegalStateException("A runner job payload failed to serialise", e);
    }

    HttpResponse<String> response;
    try {
      response =
          http.send(
              HttpRequest.newBuilder(jobs)
                  .timeout(timeout)
                  .header("content-type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                  .build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RunnerUnavailableException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RunnerUnavailableException(e);
    }

    return parse(kind, response);
  }

  private RunnerJobResult parse(String kind, HttpResponse<String> response) {
    Map<String, Object> envelope;
    try {
      envelope = json.readValue(response.body(), new TypeRef());
    } catch (IOException e) {
      log.warn("The runner answered {} with a body that is not the job envelope", kind, e);
      throw new RunnerUnavailableException(e);
    }

    if (Boolean.TRUE.equals(envelope.get("ok"))) {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) envelope.get("result");
      return RunnerJobResult.succeeded(result == null ? Map.of() : result);
    }

    // 5xx is the runner failing, not refusing; only its own refusal carries a usable reason.
    if (response.statusCode() >= 500) {
      throw new RunnerUnavailableException(
          new IOException("The runner answered " + response.statusCode()));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) envelope.get("error");
    String code = error == null ? "unknown" : String.valueOf(error.get("code"));
    String message = error == null ? "" : String.valueOf(error.get("message"));
    return RunnerJobResult.refused(code, message);
  }

  @Override
  public boolean isAvailable() {
    try {
      HttpResponse<Void> response =
          http.send(
              HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(2)).GET().build(),
              HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Jackson needs the generic shape spelled out; a nested class keeps the call sites readable. */
  private static final class TypeRef
      extends com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> {}
}
