package dev.specra.api.feature.environment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One name/value pair a run injects into the runner's process environment.
 *
 * <p>{@code value} holds ciphertext when {@code secret} is set, and plaintext otherwise — the same
 * column either way, because whether something is a secret is a property of the row and not of the
 * schema. Nothing outside {@code EnvironmentService} may read a secret value: the API answers "set"
 * or "not set", and the only place plaintext exists is in memory at dispatch.
 */
@Entity
@Table(name = "environment_vars")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EnvironmentVariable {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "environment_id", nullable = false)
  private Environment environment;

  /** {@code key} is a keyword in enough dialects to be worth quoting in the migration. */
  @Column(name = "key", nullable = false, length = 120)
  private String key;

  @Column(nullable = false, columnDefinition = "text")
  private String value;

  @Column(name = "is_secret", nullable = false)
  private boolean secret;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
