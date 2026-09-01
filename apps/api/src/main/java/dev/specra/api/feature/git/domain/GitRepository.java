package dev.specra.api.feature.git.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * The connection between one project and its one repository — where it is, how to reach it, and
 * which branch operations act on right now.
 *
 * <p>What is deliberately <em>not</em> here: file bodies, working-copy paths, anything a clone
 * could not rebuild. Git is the source of truth for code; this row only knows how to find it.
 */
@Entity
@Table(name = "git_repositories")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GitRepository {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private GitProviderKind provider;

  @Column(name = "remote_url", nullable = false, length = 500)
  private String remoteUrl;

  @Column(name = "default_branch", nullable = false, length = 200)
  private String defaultBranch;

  /** The stored credential this remote is reached with; null for public or local remotes. */
  @Column(name = "credential_id")
  private UUID credentialId;

  /** What checkout moves; null means the default branch. */
  @Column(name = "active_branch", length = 200)
  private String activeBranch;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public String effectiveBranch() {
    return activeBranch == null || activeBranch.isBlank() ? defaultBranch : activeBranch;
  }
}
