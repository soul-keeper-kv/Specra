package dev.specra.api.feature.environment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.environment.domain.Environment;
import dev.specra.api.feature.environment.domain.EnvironmentRepository;
import dev.specra.api.feature.environment.dto.EnvironmentRequest;
import dev.specra.api.feature.environment.dto.EnvironmentVariableRequest;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.support.TestProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The rules that make an environment safe to store, asserted without a database.
 *
 * <p>A real {@link SecretsCipher} rather than a mock: "the value in the row is not the plaintext"
 * is the property under test, and a stubbed cipher would assert only that a method was called.
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();

  /** 32 bytes, base64 — enough for AES-256, and obviously not a real key. */
  private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Mock EnvironmentRepository repository;
  @Mock ProjectService projects;

  EnvironmentService service;

  @BeforeEach
  void setUp() {
    service = build(new SecretsCipher(TestProperties.withEncryptionKey(KEY)));
  }

  private EnvironmentService build(SecretsCipher cipher) {
    lenient().when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    lenient().when(repository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
    return new EnvironmentService(repository, projects, cipher);
  }

  private EnvironmentRequest request(EnvironmentVariableRequest... variables) {
    return new EnvironmentRequest("STAGING", "https://staging.acme.dev", true, List.of(variables));
  }

  @Test
  void aSecretIsEncryptedAtRestAndNeverReadBack() {
    var response =
        service.create(
            PROJECT,
            request(
                new EnvironmentVariableRequest("BASE_USER", "qa@acme.dev", false),
                new EnvironmentVariableRequest("QA_PASSWORD", "hunter2", true)));

    var plain = response.variables().stream().filter(v -> !v.secret()).findFirst().orElseThrow();
    var secret = response.variables().stream().filter(v -> v.secret()).findFirst().orElseThrow();

    assertThat(plain.value()).isEqualTo("qa@acme.dev");
    // The whole point: the response has no value to leak, but does say one is configured.
    assertThat(secret.value()).isNull();
    assertThat(secret.valueSet()).isTrue();
  }

  /** What the runner receives — the only place plaintext is allowed to exist. */
  @Test
  void dispatchResolvesSecretsBackToPlaintext() {
    Environment stored = new Environment();
    stored.setWorkspaceId(WORKSPACE);
    stored.setProjectId(PROJECT);
    stored.setName("STAGING");
    stored.setBaseUrl("https://staging.acme.dev");

    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(stored));

    SecretsCipher cipher = new SecretsCipher(TestProperties.withEncryptionKey(KEY));
    var secret = new dev.specra.api.feature.environment.domain.EnvironmentVariable();
    secret.setKey("QA_PASSWORD");
    secret.setSecret(true);
    secret.setValue(cipher.encrypt("hunter2"));
    var plain = new dev.specra.api.feature.environment.domain.EnvironmentVariable();
    plain.setKey("BASE_USER");
    plain.setValue("qa@acme.dev");
    stored.replaceVariables(List.of(secret, plain));

    // The stored column really does hold ciphertext, not the password.
    assertThat(secret.getValue()).isNotEqualTo("hunter2");

    assertThat(service.resolveForDispatch(id))
        .containsEntry("QA_PASSWORD", "hunter2")
        .containsEntry("BASE_USER", "qa@acme.dev");
  }

  /**
   * A response cannot carry a secret, so a client that round-trips one sends it back with no value.
   * Treating that as "clear it" would delete every credential on the first rename.
   */
  @Test
  void aSecretResentWithoutAValueKeepsTheStoredOne() {
    SecretsCipher cipher = new SecretsCipher(TestProperties.withEncryptionKey(KEY));
    String stored = cipher.encrypt("hunter2");

    Environment existing = new Environment();
    existing.setWorkspaceId(WORKSPACE);
    existing.setProjectId(PROJECT);
    existing.setName("STAGING");
    existing.setBaseUrl("https://staging.acme.dev");
    var secret = new dev.specra.api.feature.environment.domain.EnvironmentVariable();
    secret.setKey("QA_PASSWORD");
    secret.setSecret(true);
    secret.setValue(stored);
    existing.replaceVariables(List.of(secret));

    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(existing));

    service.update(id, request(new EnvironmentVariableRequest("QA_PASSWORD", null, true)));

    assertThat(existing.getVariables()).hasSize(1);
    assertThat(existing.getVariables().get(0).getValue()).isEqualTo(stored);
  }

  /** A brand new secret with no value has nothing to carry forward, so it must be refused. */
  @Test
  void aNewSecretWithNoValueIsRefusedRatherThanStoredEmpty() {
    assertThatThrownBy(
            () ->
                service.create(
                    PROJECT, request(new EnvironmentVariableRequest("QA_PASSWORD", null, true))))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.environment.secret-required");
  }

  /** Plaintext at rest is the one outcome that is never acceptable — invariant 6. */
  @Test
  void storingASecretIsRefusedWhenTheServerHasNoEncryptionKey() {
    EnvironmentService without = build(new SecretsCipher(TestProperties.withEncryptionKey("")));

    assertThatThrownBy(
            () ->
                without.create(
                    PROJECT,
                    request(new EnvironmentVariableRequest("QA_PASSWORD", "hunter2", true))))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.environment.encryption-unavailable");
  }

  /**
   * A kept key must reuse its row rather than being deleted and reinserted.
   *
   * <p>{@code (environment_id, key)} is unique, and within one flush Hibernate issues inserts
   * before orphan deletes — so a clear-and-re-add version of this passes every mock-based test and
   * fails against a real database on the second save, which is the first thing a user does.
   */
  @Test
  void savingTwiceReusesTheRowThatAlreadyHoldsEachKey() {
    Environment existing = new Environment();
    existing.setWorkspaceId(WORKSPACE);
    existing.setProjectId(PROJECT);
    var kept = new dev.specra.api.feature.environment.domain.EnvironmentVariable();
    kept.setKey("BASE_USER");
    kept.setValue("old@acme.dev");
    var dropped = new dev.specra.api.feature.environment.domain.EnvironmentVariable();
    dropped.setKey("GONE");
    dropped.setValue("x");
    existing.replaceVariables(List.of(kept, dropped));

    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(existing));

    service.update(id, request(new EnvironmentVariableRequest("BASE_USER", "new@acme.dev", false)));

    assertThat(existing.getVariables()).hasSize(1);
    // The same object, updated — not a replacement carrying the same key.
    assertThat(existing.getVariables().get(0)).isSameAs(kept);
    assertThat(kept.getValue()).isEqualTo("new@acme.dev");
  }

  @Test
  void theFirstEnvironmentOfAProjectBecomesItsDefault() {
    when(repository.findByProjectIdOrderByNameAsc(PROJECT)).thenReturn(List.of());

    var response =
        service.create(
            PROJECT,
            new EnvironmentRequest("STAGING", "https://staging.acme.dev", false, List.of()));

    assertThat(response.isDefault()).isTrue();
  }

  @Test
  void aDuplicateNameInOneProjectIsAConflict() {
    when(repository.existsByProjectIdAndName(PROJECT, "STAGING")).thenReturn(true);

    assertThatThrownBy(() -> service.create(PROJECT, request()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.environment.duplicate-name");
  }
}
