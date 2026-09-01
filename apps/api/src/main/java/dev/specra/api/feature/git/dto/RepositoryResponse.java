package dev.specra.api.feature.git.dto;

import dev.specra.api.feature.git.domain.GitProviderKind;
import java.time.Instant;
import java.util.UUID;

/** The connection as stored; never a token, never a working-copy path. */
public record RepositoryResponse(
    UUID projectId,
    GitProviderKind provider,
    String remoteUrl,
    String defaultBranch,
    String activeBranch,
    UUID credentialId,
    Instant connectedAt,
    Instant updatedAt) {}
