package dev.specra.api.feature.git.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitRepositoryRepository extends JpaRepository<GitRepository, UUID> {

  Optional<GitRepository> findByProjectId(UUID projectId);
}
