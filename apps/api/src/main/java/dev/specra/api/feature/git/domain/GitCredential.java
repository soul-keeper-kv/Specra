package dev.specra.api.feature.git.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
 * A token for reaching remotes, stored once per workspace and referenced by id — one PAT commonly
 * unlocks several repositories, and the GitHub App tokens that replace PATs later attach to an
 * installation, not a repo. The token is {@code SecretsCipher} ciphertext; reads say "set".
 */
@Entity
@Table(name = "git_credentials")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GitCredential {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 120)
  private String username;

  @Column(name = "token_cipher", nullable = false, columnDefinition = "text")
  private String tokenCipher;

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
