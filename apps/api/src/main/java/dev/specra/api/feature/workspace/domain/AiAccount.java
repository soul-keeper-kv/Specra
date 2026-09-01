package dev.specra.api.feature.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The workspace's own model provider, key and budget — bring-your-own-key.
 *
 * <p>It hangs off the workspace and not the project because a key and a budget belong to whoever
 * pays, and that is the account. A workspace without one falls back to the installation's
 * configuration; a workspace with neither is <em>unconfigured</em>, a state the product reports and
 * offers to fix, never a failure.
 *
 * <p>{@code provider} is a lower-case identifier ({@code spring.ai.model.chat} vocabulary), never a
 * class: naming a vendor in code is the line this entity must not cross either.
 */
@Entity
@Table(name = "ai_accounts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AiAccount {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 32)
  private String provider;

  /** AES-GCM ciphertext from {@code SecretsCipher}; the plaintext never reaches this row. */
  @Column(name = "api_key_cipher", columnDefinition = "text")
  private String apiKeyCipher;

  @Column(name = "chat_model", length = 120)
  private String chatModel;

  @Column(name = "embedding_model", length = 120)
  private String embeddingModel;

  /** Soft ceiling for a month's model spend; enforcement arrives with generation records. */
  @Column(name = "monthly_budget_usd", precision = 12, scale = 2)
  private BigDecimal monthlyBudgetUsd;

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
