package dev.specra.api.feature.ai.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.feature.ai.dto.AiHealth;
import dev.specra.api.feature.ai.dto.AiHealthStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Asks the chat provider whether it is actually reachable, on a schedule, and remembers the answer.
 *
 * <p>Every other AI endpoint discovers a broken provider the way a user does: by failing a request
 * the user cared about. That is too late to be useful — the assistant is a feature people decide
 * whether to start relying on, and "is it working right now" is a question the UI should be able to
 * answer before anyone types into it. So one cheap call runs on a timer and the result is cached;
 * {@code GET /api/ai/health} reads the cache and never calls a provider on a request thread.
 *
 * <p>The probe is a real generation rather than a ping at the transport, because the failures worth
 * catching — a revoked key, an exhausted quota, a model name that no longer exists, an Ollama
 * daemon that is up but has not pulled the model — all accept a TCP connection perfectly happily.
 * It is kept to one word in and {@link #PROBE_MAX_TOKENS} out, so running it every few minutes
 * costs a rounding error even on a metered provider.
 *
 * <p>No vendor is named: the options are the portable {@link ChatOptions}, and the only {@link
 * ChatModel} in the context is whichever one {@code spring.ai.model.chat} selected.
 */
@Service
public class AiHealthService {

  private static final Logger log = LoggerFactory.getLogger(AiHealthService.class);

  /**
   * Enough for the model to emit something and stop. The answer is discarded — that a response came
   * back at all is the whole signal.
   */
  private static final int PROBE_MAX_TOKENS = 1;

  private static final String PROBE_TEXT = "ping";

  private final ChatModel chatModel;
  private final AiProviders providers;
  private final AiCredentials credentials;
  private final AiFailures failures;
  private final SpecraProperties.Ai.Health config;

  private final AtomicReference<AiHealth> snapshot;

  /** One probe at a time. A caller that arrives mid-probe reads the cache instead of queueing. */
  private final ReentrantLock probing = new ReentrantLock();

  /**
   * The probe runs here rather than on the scheduler thread so that the configured timeout can be
   * enforced: a provider that accepts the connection and then never answers would otherwise hold
   * the scheduler for as long as it liked, and every later task with it. A cancelled probe's thread
   * stays busy until the underlying HTTP call gives up, which is why this pool grows on demand
   * instead of being one thread that a single hung call would block forever.
   */
  private final ExecutorService probes =
      Executors.newCachedThreadPool(
          runnable -> {
            Thread thread = new Thread(runnable, "ai-health-probe");
            thread.setDaemon(true);
            return thread;
          });

  public AiHealthService(
      ChatModel chatModel,
      AiProviders providers,
      AiCredentials credentials,
      AiFailures failures,
      SpecraProperties properties,
      MeterRegistry meters) {
    this.chatModel = chatModel;
    this.providers = providers;
    this.credentials = credentials;
    this.failures = failures;
    this.config = properties.ai().health();
    this.snapshot = new AtomicReference<>(AiHealth.unknown(providers.chat()));

    // Scraped by Prometheus through the actuator endpoint that is already exposed, so a provider
    // going down is visible on a dashboard and not only to whoever happens to open the app. A
    // number rather than a status string, because that is what a gauge is; UNKNOWN reads as 0 and
    // is not yet something to alert on, which is what the interval is for.
    meters.gauge(
        "specra.ai.provider.up",
        List.of(Tag.of("provider", providers.chat())),
        this,
        service -> service.snapshot.get().status() == AiHealthStatus.UP ? 1 : 0);
  }

  /** The cached reading. Never calls a provider, so it is safe on a request thread. */
  public AiHealth current() {
    return snapshot.get();
  }

  /**
   * Probe now — for a "test connection" button rather than for the timer.
   *
   * <p>Debounced by {@code min-refresh-interval}: the endpoint is unauthenticated and the call
   * behind it is billable, so a client holding down refresh must not be able to turn one page into
   * a stream of provider requests. Under the debounce the caller gets the cached reading, which is
   * the truthful answer anyway — it is seconds old.
   */
  public AiHealth refresh() {
    AiHealth last = snapshot.get();
    if (last.checkedAt() != null
        && Duration.between(last.checkedAt(), Instant.now()).compareTo(config.minRefreshInterval())
            < 0) {
      return last;
    }
    return check();
  }

  /**
   * The timer. Guarded by the property inside the method rather than by {@code
   * ConditionalOnProperty} on the bean, so that switching the schedule off still leaves {@link
   * #refresh()} working for a deployment that wants the button without the polling.
   *
   * <p>The delay strings are ISO-8601 ({@code PT5M}) because that is the only form {@code
   * Scheduled} parses: {@code 5m} would bind fine as a configuration property and then fail at
   * startup here. One value, one format, both sides.
   */
  @Scheduled(
      fixedDelayString = "${specra.ai.health.interval:PT5M}",
      initialDelayString = "${specra.ai.health.initial-delay:PT10S}")
  void scheduledCheck() {
    if (!config.enabled()) {
      return;
    }
    check();
  }

  /** Runs a probe and stores what it found, unless another probe is already in flight. */
  AiHealth check() {
    if (!probing.tryLock()) {
      return snapshot.get();
    }
    try {
      AiHealth previous = snapshot.get();
      AiHealth result = probe(previous);
      snapshot.set(result);
      report(previous, result);
      return result;
    } finally {
      probing.unlock();
    }
  }

  private AiHealth probe(AiHealth previous) {
    String provider = providers.chat();
    if (!credentials.chatIsConfigured()) {
      // Nothing to call with. Its own status, because the fix is a key and not a retry.
      return reading(
          previous, AiHealthStatus.NOT_CONFIGURED, provider, ErrorCode.AI_NOT_CONFIGURED, null);
    }

    long startedAt = System.nanoTime();
    Future<?> answer;
    try {
      answer = probes.submit(() -> chatModel.call(prompt()));
    } catch (RejectedExecutionException e) {
      // Shutdown, or every probe thread still stuck on a hung provider. Both read as unavailable.
      return down(previous, provider, ErrorCode.AI_PROVIDER_UNAVAILABLE, null);
    }

    try {
      answer.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
      return reading(previous, AiHealthStatus.UP, provider, null, elapsedMillis(startedAt));
    } catch (TimeoutException e) {
      answer.cancel(true);
      log.warn(
          "Chat provider '{}' did not answer a health probe within {}", provider, config.timeout());
      return down(previous, provider, ErrorCode.AI_PROVIDER_UNAVAILABLE, elapsedMillis(startedAt));
    } catch (ExecutionException e) {
      // AiFailures owns the mapping from a provider's exception to a code, and logs the original in
      // full — so a probe failure and a failed question are classified by the same rules.
      Throwable cause = e.getCause() == null ? e : e.getCause();
      return down(
          previous, provider, failures.translate(cause).errorCode(), elapsedMillis(startedAt));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      answer.cancel(true);
      return previous;
    }
  }

  private AiHealth down(AiHealth previous, String provider, ErrorCode code, Long latencyMillis) {
    return reading(previous, AiHealthStatus.DOWN, provider, code, latencyMillis);
  }

  private AiHealth reading(
      AiHealth previous,
      AiHealthStatus status,
      String provider,
      ErrorCode code,
      Long latencyMillis) {
    Instant checkedAt = Instant.now();
    int failureCount = status == AiHealthStatus.UP ? 0 : previous.consecutiveFailures() + 1;
    Instant nextCheckAt = config.enabled() ? checkedAt.plus(config.interval()) : null;
    return new AiHealth(
        status,
        provider,
        code == null ? null : code.slug(),
        checkedAt,
        latencyMillis,
        nextCheckAt,
        failureCount);
  }

  /**
   * One line per transition, not one per probe: a provider that is down for an hour should not be
   * twelve identical warnings, and a recovery is the event nobody sees otherwise.
   */
  private void report(AiHealth previous, AiHealth current) {
    if (previous.status() == current.status()) {
      return;
    }
    if (current.status() == AiHealthStatus.UP) {
      log.info(
          "Chat provider '{}' is answering again ({} ms)",
          current.chatProvider(),
          current.latencyMillis());
      return;
    }
    log.warn(
        "Chat provider '{}' is now {} ({})",
        current.chatProvider(),
        current.status(),
        current.code());
  }

  private Prompt prompt() {
    return new Prompt(
        List.of(new UserMessage(PROBE_TEXT)),
        ChatOptions.builder().maxTokens(PROBE_MAX_TOKENS).temperature(0.0).build());
  }

  private static long elapsedMillis(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
  }

  @PreDestroy
  void stopProbing() {
    probes.shutdownNow();
  }
}
