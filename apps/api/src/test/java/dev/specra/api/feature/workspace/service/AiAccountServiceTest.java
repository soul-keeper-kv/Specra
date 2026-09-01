package dev.specra.api.feature.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.workspace.domain.AiAccount;
import dev.specra.api.feature.workspace.domain.AiAccountRepository;
import dev.specra.api.feature.workspace.dto.AiAccountRequest;
import dev.specra.api.support.TestProperties;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAccountServiceTest {

  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final String KEY_32 =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  @Mock AiAccountRepository repository;
  @Mock WorkspaceService workspaces;

  @Test
  void storesTheKeyEncryptedAndReportsOnlyThatItIsSet() {
    AiAccountService service = service(KEY_32);
    when(repository.findByWorkspaceId(WORKSPACE)).thenReturn(Optional.empty());
    saveReturnsWhatItWasGiven();

    var response =
        service.put(WORKSPACE, new AiAccountRequest("Anthropic", "sk-secret", null, null, null));

    assertThat(response.keySet()).isTrue();
    assertThat(response.provider()).isEqualTo("anthropic");
    // The response type cannot even carry the key; the row must not carry it readably.
    assertThat(captureSaved().getApiKeyCipher()).isNotBlank().doesNotContain("sk-secret");
  }

  /** Re-saving the settings form without retyping the key must not wipe it. */
  @Test
  void aNullKeyKeepsTheStoredOneAndABlankKeyClearsIt() {
    AiAccountService service = service(KEY_32);
    AiAccount existing = new AiAccount();
    existing.setWorkspaceId(WORKSPACE);
    existing.setProvider("openai");
    existing.setApiKeyCipher("cipher-bytes");
    when(repository.findByWorkspaceId(WORKSPACE)).thenReturn(Optional.of(existing));
    saveReturnsWhatItWasGiven();

    var kept = service.put(WORKSPACE, new AiAccountRequest("openai", null, "gpt-4o", null, null));
    assertThat(kept.keySet()).isTrue();
    assertThat(kept.chatModel()).isEqualTo("gpt-4o");

    var cleared = service.put(WORKSPACE, new AiAccountRequest("openai", "", null, null, null));
    assertThat(cleared.keySet()).isFalse();
  }

  /** Refusing beats the alternative, which is a plaintext key at rest. */
  @Test
  void refusesToStoreAKeyWhenTheServerHasNoEncryptionKey() {
    AiAccountService service = service("");
    when(repository.findByWorkspaceId(WORKSPACE)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.put(
                    WORKSPACE, new AiAccountRequest("anthropic", "sk-secret", null, null, null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("encryption-unavailable");
  }

  @Test
  void answersWhetherAWorkspaceBringsItsOwnKeyForAProvider() {
    AiAccountService service = service(KEY_32);
    AiAccount account = new AiAccount();
    account.setWorkspaceId(WORKSPACE);
    account.setProvider("anthropic");
    account.setApiKeyCipher("cipher");
    when(repository.findByWorkspaceId(WORKSPACE)).thenReturn(Optional.of(account));

    assertThat(service.hasUsableKey(WORKSPACE, "anthropic")).isTrue();
    assertThat(service.hasUsableKey(WORKSPACE, "openai")).isFalse();
  }

  private AiAccountService service(String encryptionKey) {
    return new AiAccountService(
        repository, workspaces, new SecretsCipher(TestProperties.withEncryptionKey(encryptionKey)));
  }

  private AiAccount saved;

  private void saveReturnsWhatItWasGiven() {
    when(repository.save(any(AiAccount.class)))
        .thenAnswer(
            inv -> {
              saved = inv.getArgument(0);
              return saved;
            });
  }

  private AiAccount captureSaved() {
    assertThat(saved).isNotNull();
    return saved;
  }
}
