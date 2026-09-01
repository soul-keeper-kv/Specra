package dev.specra.api.feature.workspace.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  boolean existsBySlug(String slug);
}
