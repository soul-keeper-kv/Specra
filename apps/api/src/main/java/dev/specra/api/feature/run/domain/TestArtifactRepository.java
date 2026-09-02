package dev.specra.api.feature.run.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestArtifactRepository extends JpaRepository<TestArtifact, UUID> {

  /**
   * Artifacts past their expiry, oldest first and in batches.
   *
   * <p>Paged rather than "find them all": a neglected instance can have a very large backlog, and
   * loading it into one list is how a cleanup job becomes an outage.
   */
  List<TestArtifact> findByExpiresAtBefore(Instant cutoff, Pageable pageable);
}
