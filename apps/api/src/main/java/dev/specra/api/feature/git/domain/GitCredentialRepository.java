package dev.specra.api.feature.git.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitCredentialRepository extends JpaRepository<GitCredential, UUID> {

  List<GitCredential> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

  Optional<GitCredential> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
