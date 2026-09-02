package dev.specra.api.feature.run.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

  Page<TestRun> findByProjectIdOrderByQueuedAtDesc(UUID projectId, Pageable pageable);

  Optional<TestRun> findByProjectIdAndReference(UUID projectId, String reference);

  /** How many of a workspace's runs are in flight, for the per-workspace concurrency limit. */
  long countByWorkspaceIdAndStatusIn(UUID workspaceId, java.util.Collection<RunStatus> statuses);
}
