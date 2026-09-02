package dev.specra.api.feature.environment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

  List<Environment> findByProjectIdOrderByNameAsc(UUID projectId);

  Optional<Environment> findByProjectIdAndIsDefaultTrue(UUID projectId);

  boolean existsByProjectIdAndName(UUID projectId, String name);

  Optional<Environment> findByProjectIdAndName(UUID projectId, String name);
}
