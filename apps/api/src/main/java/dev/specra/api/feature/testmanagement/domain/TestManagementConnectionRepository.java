package dev.specra.api.feature.testmanagement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestManagementConnectionRepository
    extends JpaRepository<TestManagementConnection, UUID> {
  List<TestManagementConnection> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

  Optional<TestManagementConnection> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
