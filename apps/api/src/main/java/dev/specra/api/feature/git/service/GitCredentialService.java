package dev.specra.api.feature.git.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.git.GitAuth;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.git.domain.GitCredential;
import dev.specra.api.feature.git.domain.GitCredentialRepository;
import dev.specra.api.feature.git.dto.CredentialRequest;
import dev.specra.api.feature.git.dto.CredentialResponse;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tokens for reaching remotes: stored encrypted, listed by name, never read back out through the
 * API. The one method that does decrypt — {@link #authFor} — hands the plaintext straight to a
 * provider call and keeps no copy, which is the whole reason it is package-private.
 */
@Service
@Transactional(readOnly = true)
public class GitCredentialService {

  private final GitCredentialRepository repository;
  private final WorkspaceService workspaces;
  private final SecretsCipher cipher;

  public GitCredentialService(
      GitCredentialRepository repository, WorkspaceService workspaces, SecretsCipher cipher) {
    this.repository = repository;
    this.workspaces = workspaces;
    this.cipher = cipher;
  }

  public List<CredentialResponse> list(UUID workspaceId) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_VIEW);
    return repository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
        .map(GitCredentialService::toResponse)
        .toList();
  }

  @Transactional
  public CredentialResponse create(UUID workspaceId, CredentialRequest request) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_UPDATE);

    String name = request.name().trim();
    if (repository.existsByWorkspaceIdAndName(workspaceId, name)) {
      throw new ConflictException("error.git.credential-duplicate-name", name);
    }
    if (!cipher.isEnabled()) {
      throw new ConflictException("error.git.encryption-unavailable");
    }

    GitCredential credential = new GitCredential();
    credential.setWorkspaceId(workspaceId);
    credential.setName(name);
    credential.setUsername(
        StringUtils.hasText(request.username()) ? request.username().trim() : null);
    credential.setTokenCipher(cipher.encrypt(request.token().trim()));
    return toResponse(repository.save(credential));
  }

  @Transactional
  public void delete(UUID workspaceId, UUID id) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_UPDATE);
    repository.delete(
        repository
            .findByIdAndWorkspaceId(id, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("resource.git-credential", id)));
  }

  /**
   * Decrypts one credential for the length of a single provider call.
   *
   * <p>Package-private on purpose: nothing outside this feature — and no controller inside it — can
   * reach a plaintext token. The workspace is part of the lookup so a repository row cannot name a
   * credential belonging to another tenant.
   */
  GitAuth authFor(UUID workspaceId, UUID credentialId) {
    GitCredential credential =
        repository
            .findByIdAndWorkspaceId(credentialId, workspaceId)
            .orElseThrow(
                () -> new ResourceNotFoundException("resource.git-credential", credentialId));
    return new GitAuth(credential.getUsername(), cipher.decrypt(credential.getTokenCipher()));
  }

  private static CredentialResponse toResponse(GitCredential credential) {
    return new CredentialResponse(
        credential.getId(),
        credential.getName(),
        credential.getUsername(),
        StringUtils.hasText(credential.getTokenCipher()),
        credential.getUpdatedAt());
  }
}
