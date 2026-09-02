package dev.specra.api.core.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Where run evidence lives (06-execution.md).
 *
 * <p>A port for the same reason {@code GitProvider} and {@code RunnerClient} are ports: S3 is one
 * answer, a directory on disk is another, and the feature above must not be able to tell. Postgres
 * is never an answer — a trace is megabytes and a QA team produces a great many of them.
 *
 * <p>The two rules this interface exists to enforce: **the API never proxies bytes**, so reads are
 * a {@link #url} rather than a stream; and every link **expires**, so one embedded in a cached page
 * or pasted into a ticket stops working rather than becoming a permanent public handle to somebody
 * else's test evidence.
 */
public interface ObjectStore {

  /** Stable, lower-case, for logs and configuration. */
  String kind();

  /**
   * Stores a file and returns nothing: the caller already knows the key it asked for.
   *
   * @param key the full storage key, e.g. {@code runs/{runId}/{itemId}/trace.zip}
   */
  void put(String key, Path file, String contentType);

  /** Reads an object back, for the API's own use — never to stream at a browser. */
  Optional<InputStream> open(String key);

  /**
   * A URL a browser can fetch directly, valid for {@code ttl} and no longer.
   *
   * <p>Empty when this store cannot mint one, which the caller turns into an honest "evidence is
   * not reachable" rather than a broken link.
   */
  Optional<String> url(String key, Duration ttl);

  /** Removes an object; called by retention, and idempotent so a repeat sweep is harmless. */
  void delete(String key);
}
