package dev.specra.api.feature.run.service;

import dev.specra.api.core.storage.ObjectStore;
import dev.specra.api.feature.run.domain.TestArtifact;
import dev.specra.api.feature.run.domain.TestArtifactRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes evidence that has outlived its retention.
 *
 * <p>Without this, {@code expires_at} would be a column nobody honours — a promise in the schema
 * that the disk quietly breaks. Traces are megabytes and a QA team runs a great many tests, so the
 * sweep is what keeps storage a cost rather than a leak.
 *
 * <p>Two things it is careful about. The bytes go first and the row second, because a row without a
 * file is a broken link the user can see, while a file without a row is invisible waste the next
 * sweep does not even know about. And it works in batches with a ceiling per pass: a backlog of a
 * hundred thousand artifacts must not become one transaction that holds a connection for minutes.
 */
@Component
public class ArtifactRetention {

  private static final Logger log = LoggerFactory.getLogger(ArtifactRetention.class);

  /** Per pass. A backlog drains over several sweeps rather than in one long transaction. */
  private static final int BATCH = 500;

  private final TestArtifactRepository artifacts;
  private final ObjectStore store;

  public ArtifactRetention(TestArtifactRepository artifacts, ObjectStore store) {
    this.artifacts = artifacts;
    this.store = store;
  }

  /**
   * Hourly, offset from startup.
   *
   * <p>Retention is measured in days, so the exact hour does not matter; what matters is that a
   * long-running instance does not accumulate for ever, and that a restart does not trigger a sweep
   * while the application is still coming up.
   */
  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
  public void sweep() {
    int deleted = deleteExpired();
    if (deleted > 0) {
      log.info("Retention removed {} expired artifacts", deleted);
    }
  }

  /**
   * One pass. Returns how many were removed, so a test can assert on it rather than on a log.
   *
   * <p>A file that has already gone is not an error: object stores are eventually consistent,
   * sweeps overlap, and the row still has to go. A file that cannot be deleted for a real reason
   * leaves its row alone, so the next pass tries again rather than orphaning the bytes for ever.
   */
  @Transactional
  public int deleteExpired() {
    List<TestArtifact> expired =
        artifacts.findByExpiresAtBefore(Instant.now(), PageRequest.of(0, BATCH));

    int removed = 0;
    for (TestArtifact artifact : expired) {
      try {
        store.delete(artifact.getStorageKey());
        artifacts.delete(artifact);
        removed++;
      } catch (RuntimeException e) {
        log.warn("Could not remove expired artifact {}", artifact.getStorageKey(), e);
      }
    }
    return removed;
  }
}
