package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.workspace.domain.AiAccount;
import dev.specra.api.feature.workspace.domain.AiAccountRepository;
import dev.specra.api.feature.workspace.dto.AiAccountRequest;
import dev.specra.api.feature.workspace.dto.AiAccountResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The workspace's bring-your-own-key account.
 *
 * <p>Three rules do all the work here. The key is encrypted before it is saved and is never read
 * back out through this service — callers learn <em>that</em> a key is set, not what it is. A
 * request without a key keeps whatever is stored, so re-saving the form does not wipe it. And when
 * no encryption key is configured on the server, storing one is refused with the variable to set,
 * because the alternative — plaintext at rest — is the invariant this feature must not break.
 */
@Service
@Transactional(readOnly = true)
public class AiAccountService {

  private final AiAccountRepository repository;
  private final WorkspaceService workspaces;
  private final SecretsCipher cipher;

  public AiAccountService(
      AiAccountRepository repository, WorkspaceService workspaces, SecretsCipher cipher) {
    this.repository = repository;
    this.workspaces = workspaces;
    this.cipher = cipher;
  }

  public AiAccountResponse get(UUID workspaceId) {
    workspaces.requireExists(workspaceId);
    return toResponse(require(workspaceId));
  }

  @Transactional
  public AiAccountResponse put(UUID workspaceId, AiAccountRequest request) {
    workspaces.requireExists(workspaceId);

    AiAccount account =
        repository
            .findByWorkspaceId(workspaceId)
            .orElseGet(
                () -> {
                  AiAccount created = new AiAccount();
                  created.setWorkspaceId(workspaceId);
                  return created;
                });

    account.setProvider(request.provider().trim().toLowerCase(Locale.ROOT));
    account.setChatModel(trimToNull(request.chatModel()));
    account.setEmbeddingModel(trimToNull(request.embeddingModel()));
    account.setMonthlyBudgetUsd(request.monthlyBudgetUsd());
    applyKey(account, request.apiKey());

    return toResponse(repository.save(account));
  }

  /** Removes the override entirely; the workspace falls back to the installation's provider. */
  @Transactional
  public void delete(UUID workspaceId) {
    workspaces.requireExists(workspaceId);
    repository.delete(require(workspaceId));
  }

  /**
   * Whether this workspace brings its own usable key for {@code provider}. The seam {@code
   * AiCredentials} consults before falling back to the platform's configuration — deliberately a
   * yes/no, so the key itself never travels further than the client that will be built from it.
   */
  public boolean hasUsableKey(UUID workspaceId, String provider) {
    return repository
        .findByWorkspaceId(workspaceId)
        .filter(account -> account.getProvider().equalsIgnoreCase(provider))
        .filter(account -> StringUtils.hasText(account.getApiKeyCipher()))
        .isPresent();
  }

  private AiAccount require(UUID workspaceId) {
    return repository
        .findByWorkspaceId(workspaceId)
        .orElseThrow(() -> new ResourceNotFoundException("resource.ai-account", workspaceId));
  }

  /** Null keeps, blank clears, a value is encrypted and stored. */
  private void applyKey(AiAccount account, String apiKey) {
    if (apiKey == null) {
      return;
    }
    if (apiKey.isBlank()) {
      account.setApiKeyCipher(null);
      return;
    }
    if (!cipher.isEnabled()) {
      throw new ConflictException("error.ai-account.encryption-unavailable");
    }
    account.setApiKeyCipher(cipher.encrypt(apiKey.trim()));
  }

  private static AiAccountResponse toResponse(AiAccount account) {
    return new AiAccountResponse(
        account.getWorkspaceId(),
        account.getProvider(),
        account.getChatModel(),
        account.getEmbeddingModel(),
        account.getMonthlyBudgetUsd(),
        StringUtils.hasText(account.getApiKeyCipher()),
        account.getUpdatedAt());
  }

  private static String trimToNull(String text) {
    return StringUtils.hasText(text) ? text.trim() : null;
  }
}
