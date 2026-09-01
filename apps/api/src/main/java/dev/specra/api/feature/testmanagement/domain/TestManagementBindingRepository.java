package dev.specra.api.feature.testmanagement.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestManagementBindingRepository
    extends JpaRepository<TestManagementBinding, UUID> {
  Optional<TestManagementBinding> findByProjectId(UUID projectId);

  boolean existsByConnectionId(UUID connectionId);
}
