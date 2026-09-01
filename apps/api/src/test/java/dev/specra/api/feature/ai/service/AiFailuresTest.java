package dev.specra.api.feature.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Flux;

/**
 * A missing or rejected key must never reach the caller as {@code internal-error}.
 *
 * <p>That is not a cosmetic preference. "Something went wrong on our side" is false — nothing went
 * wrong on our side — and it tells the one person who could fix it in half a minute to go and read
 * a stack trace instead.
 */
class AiFailuresTest {

  private static final String PROVIDER = "acme";

  private AiFailures failuresWith(String apiKey) {
    MockEnvironment environment = new MockEnvironment();
    if (apiKey != null) {
      environment.setProperty("spring.ai." + PROVIDER + ".api-key", apiKey);
    }
    AiProviders providers =
        new AiProviders(PROVIDER, "embed", mock(ChatModel.class), mock(EmbeddingModel.class));
    return new AiFailures(new AiCredentials(environment, providers), providers);
  }

  private AiFailures configured() {
    return failuresWith("sk-a-real-looking-key");
  }

  @Test
  @DisplayName("no key: refuses before calling out, and says which provider needs one")
  void refusesWhenNotConfigured() {
    assertThatThrownBy(() -> configuredWithoutKey().guard(() -> "never runs"))
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(ErrorCode.AI_NOT_CONFIGURED);

    assertThat(ErrorCode.AI_NOT_CONFIGURED.status().value()).isEqualTo(503);
  }

  private AiFailures configuredWithoutKey() {
    return failuresWith(AiCredentials.UNSET);
  }

  @Test
  @DisplayName("a blank key counts as no key, not as a key that happens to be empty")
  void blankKeyIsNotConfigured() {
    assertThatThrownBy(() -> failuresWith("   ").guard(() -> "never runs"))
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
  }

  @Test
  @DisplayName("a provider that declares no key at all needs none")
  void providerWithoutAKeyPropertyIsConfigured() {
    assertThat(failuresWith(null).guard(() -> "ran")).isEqualTo("ran");
  }

  /** The exact shape the provider threw in production: a bare RuntimeException with the status. */
  @ParameterizedTest(name = "upstream {0} -> {1}")
  @CsvSource({
    "401, AI_PROVIDER_ERROR",
    "403, AI_PROVIDER_ERROR",
    "404, AI_PROVIDER_ERROR",
    "429, RATE_LIMITED",
    "500, AI_PROVIDER_UNAVAILABLE",
    "503, AI_PROVIDER_UNAVAILABLE",
  })
  void classifiesByUpstreamStatus(int status, ErrorCode expected) {
    RuntimeException fromProvider =
        new RuntimeException(
            "Response exception, Status: [" + status + " SOMETHING], Body:[{\"type\":\"error\"}]");

    assertThatThrownBy(
            () ->
                configured()
                    .guard(
                        () -> {
                          throw fromProvider;
                        }))
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("the 401 that started this is never internal-error")
  void theOriginalFailureIsNotReportedAsOurBug() {
    RuntimeException invalidKey =
        new RuntimeException(
            "Response exception, Status: [401 UNAUTHORIZED],"
                + " Body:[{\"error\":{\"message\":\"invalid x-api-key\"}}]");

    BusinessException reported =
        (BusinessException)
            org.assertj.core.api.Assertions.catchThrowable(
                () ->
                    configured()
                        .guard(
                            () -> {
                              throw invalidKey;
                            }));

    assertThat(reported.errorCode()).isNotEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(reported.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_ERROR);
    assertThat(reported.errorCode().status().value()).isEqualTo(502);
  }

  @ParameterizedTest(name = "{0} is treated as unreachable, not as a refusal")
  @org.junit.jupiter.params.provider.MethodSource("connectivityFailures")
  void connectivityIsUnavailable(String name, Throwable cause) {
    assertThatThrownBy(
            () ->
                configured()
                    .guard(
                        () -> {
                          throw new RuntimeException("wrapped", cause);
                        }))
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
  }

  static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
      connectivityFailures() {
    return java.util.stream.Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            "connection refused", new ConnectException("Connection refused")),
        org.junit.jupiter.params.provider.Arguments.of("timeout", new TimeoutException("too slow")),
        org.junit.jupiter.params.provider.Arguments.of("socket", new IOException("broken pipe")));
  }

  @Test
  @DisplayName("a BusinessException from our own code passes through untouched")
  void ourOwnFailuresAreNotReclassified() {
    BusinessException ours =
        new BusinessException(ErrorCode.VALIDATION_FAILED, "error.validation-failed.detail");

    assertThatThrownBy(
            () ->
                configured()
                    .guard(
                        () -> {
                          throw ours;
                        }))
        .isSameAs(ours);
  }

  @Test
  @DisplayName("streaming: no key fails before a stream is opened")
  void streamRefusesEagerlyWhenNotConfigured() {
    assertThatThrownBy(() -> configuredWithoutKey().guardStream(() -> Flux.just("a", "b")))
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(ErrorCode.AI_NOT_CONFIGURED);
  }

  @Test
  @DisplayName("streaming: a failure mid-stream is reported with an AI code, not a bare error")
  void streamFailuresAreTranslated() {
    Flux<String> guarded =
        configured()
            .guardStream(
                () ->
                    Flux.just("hello")
                        .concatWith(
                            Flux.error(
                                new RuntimeException(
                                    "Response exception, Status: [401 X], Body:[]"))));

    List<String> received = new ArrayList<>();

    assertThatThrownBy(() -> guarded.doOnNext(received::add).collectList().block())
        .isInstanceOf(BusinessException.class)
        .extracting(failure -> ((BusinessException) failure).errorCode())
        .isEqualTo(ErrorCode.AI_PROVIDER_ERROR);

    // The tokens already produced still reached the subscriber; only the tail became an error.
    assertThat(received).containsExactly("hello");
  }
}
