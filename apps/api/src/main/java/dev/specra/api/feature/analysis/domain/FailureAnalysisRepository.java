package dev.specra.api.feature.analysis.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureAnalysisRepository extends JpaRepository<FailureAnalysis, UUID> {

  /** One per cell — re-analysing replaces the reading rather than adding a second one. */
  Optional<FailureAnalysis> findByTestRunItemId(UUID testRunItemId);
}
