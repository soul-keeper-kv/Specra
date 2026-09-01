package dev.specra.api.feature.workspace.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAccountRepository extends JpaRepository<AiAccount, UUID> {

  Optional<AiAccount> findByWorkspaceId(UUID workspaceId);
}
