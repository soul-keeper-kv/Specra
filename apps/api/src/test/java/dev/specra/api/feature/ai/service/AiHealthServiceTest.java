package dev.specra.api.feature.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.feature.ai.dto.AiHealth;
import dev.specra.api.feature.ai.dto.AiHealthStatus;
import dev.specra.api.support.StubChatModel;
import dev.specra.api.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.mock.env.MockEnvironment;

/**
 * The health probe is the one AI call nobody asked for, so its job is to be honest and cheap.
 *
 * <p>Honest: the four statuses have to stay distinguishable, because "add a key" and "the provider
 * is refusing us" are different sentences to a user and the endpoint is what the UI branches on.
 * Cheap: it runs unattended on a timer against a metered provider, so the one-token ceiling and the
 * refresh debounce are behaviour, not implementation detail — the assertions below are what stops
 * either quietly becoming a bill.
 */
class AiHealthServiceTest {

  private static final String PROVIDER = "acme";
  private static final String KEY = "sk-a-real-looking-key";

  @Test
  @DisplayName("a provider that answers is UP, with a latency and no code")
  void reportsUp() {
    AiHealth health = service(new StubChatModel(), KEY).check();

    assertThat(health.status()).isEqualTo(AiHealthStatus.UP);
    assertThat(health.code()).isNull();
    assertThat(health.chatProvider()).isEqualTo(PROVIDER);
    assertThat(health.latencyMillis()).isNotNull();
    assertThat(health.consecutiveFailures()).isZero();
    assertThat(health.checkedAt()).isNotNull();
    assertThat(health.nextCheckAt()).isAfter(health.checkedAt());
  }

  @Test
  @DisplayName("the probe asks for one token: running every few minutes must not cost anything")
  void probeIsCheap() {
    RecordingChatModel model = new RecordingChatModel();

    service(model, KEY).check();

    assertThat(model.lastPrompt.getOptions().getMaxTokens()).isEqualTo(1);
  }

  @Test
  @DisplayName("no key: NOT_CONFIGURED, and the provider is never called")
  void reportsNotConfigured() {
    RecordingChatModel model = new RecordingChatModel();

    AiHealth health = service(model, null).check();

    assertThat(health.status()).isEqualTo(AiHealthStatus.NOT_CONFIGURED);
    assertThat(health.code()).isEqualTo(ErrorCode.AI_NOT_CONFIGURED.slug());
    assertThat(model.calls).hasValue(0);
  }

  @Test
  @DisplayName("a failed call is DOWN, classified by the same rules a failed question is")
  void reportsDown() {
    ChatModel unreachable = mock(ChatModel.class);
    when(unreachable.call(any(Prompt.class)))
        .thenThrow(new RuntimeException("no route", new ConnectException("connection refused")));

    AiHealth health = service(unreachable, KEY).check();

    assertThat(health.status()).isEqualTo(AiHealthStatus.DOWN);
    assertThat(health.code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE.slug());
    assertThat(health.consecutiveFailures()).isEqualTo(1);
  }

  @Test
  @DisplayName("a provider that accepts the call and never answers is DOWN, not a stuck scheduler")
  void reportsTimeout() {
    // Well under the configured floor, because the point here is the timeout path and not the
    // policy: a test that waited ten seconds to prove this would never be run.
    AiHealthService service =
        service(hangsFor(Duration.ofSeconds(30)), KEY, Duration.ofMillis(300), Duration.ZERO);

    AiHealth health = service.check();

    assertThat(health.status()).isEqualTo(AiHealthStatus.DOWN);
    assertThat(health.code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE.slug());
  }

  @Test
  @DisplayName("failures accumulate and a recovery resets the count")
  void countsConsecutiveFailures() {
    FlakyChatModel model = new FlakyChatModel();
    AiHealthService service = service(model, KEY);

    model.failing = true;
    service.check();
    assertThat(service.check().consecutiveFailures()).isEqualTo(2);

    model.failing = false;
    assertThat(service.check().consecutiveFailures()).isZero();
  }

  @Test
  @DisplayName("refresh is debounced: holding down a button cannot become a stream of model calls")
  void refreshIsDebounced() {
    RecordingChatModel model = new RecordingChatModel();
    AiHealthService service = service(model, KEY);

    AiHealth first = service.refresh();
    AiHealth second = service.refresh();

    assertThat(model.calls).hasValue(1);
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("refresh still probes when the schedule is switched off")
  void refreshWorksWithTheScheduleOff() {
    RecordingChatModel model = new RecordingChatModel();
    SpecraProperties.Ai.Health config =
        TestProperties.health(false, Duration.ofSeconds(10), Duration.ofSeconds(10));

    AiHealth health = service(model, KEY, config).refresh();

    assertThat(health.status()).isEqualTo(AiHealthStatus.UP);
    // Nothing is scheduled, so there is no next check to promise the client.
    assertThat(health.nextCheckAt()).isNull();
  }

  @Test
  @DisplayName("the timer is a no-op when checks are disabled, and the reading stays UNKNOWN")
  void scheduleRespectsTheSwitch() {
    RecordingChatModel model = new RecordingChatModel();
    AiHealthService service =
        service(model, KEY, TestProperties.health(false, Duration.ofSeconds(10), Duration.ZERO));

    service.scheduledCheck();

    assertThat(model.calls).hasValue(0);
    assertThat(service.current().status()).isEqualTo(AiHealthStatus.UNKNOWN);
  }

  // ── fixtures ────────────────────────────────────────────────────────────────────────────────

  private AiHealthService service(ChatModel model, String apiKey) {
    return service(model, apiKey, Duration.ofSeconds(10), Duration.ofSeconds(10));
  }

  private AiHealthService service(
      ChatModel model, String apiKey, Duration timeout, Duration minRefreshInterval) {
    return service(model, apiKey, TestProperties.health(true, timeout, minRefreshInterval));
  }

  private AiHealthService service(
      ChatModel model, String apiKey, SpecraProperties.Ai.Health config) {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty(
        "spring.ai." + PROVIDER + ".api-key", apiKey == null ? "not-set" : apiKey);

    AiProviders providers = new AiProviders(PROVIDER, "embed", model, mock(EmbeddingModel.class));
    AiCredentials credentials = new AiCredentials(environment, providers, null);
    return new AiHealthService(
        model,
        providers,
        credentials,
        new AiFailures(credentials, providers),
        TestProperties.withAiHealth(config),
        new SimpleMeterRegistry());
  }

  private static ChatModel hangsFor(Duration duration) {
    return new StubChatModel() {
      @Override
      public ChatResponse call(Prompt prompt) {
        try {
          Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return super.call(prompt);
      }
    };
  }

  /** Remembers what the probe sent and how often, so the cost claims can be asserted. */
  private static final class RecordingChatModel extends StubChatModel {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile Prompt lastPrompt;

    @Override
    public ChatResponse call(Prompt prompt) {
      calls.incrementAndGet();
      lastPrompt = prompt;
      return super.call(prompt);
    }
  }

  private static final class FlakyChatModel extends StubChatModel {

    private volatile boolean failing;

    @Override
    public ChatResponse call(Prompt prompt) {
      if (failing) {
        throw new IllegalStateException("provider said no");
      }
      return super.call(prompt);
    }
  }
}
