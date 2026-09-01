package dev.specra.api.feature.workspace.domain;

import dev.specra.api.feature.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One person's place in one workspace: user × workspace × role.
 *
 * <p>This row is the authorisation decision. There is no global "is an admin" flag anywhere in
 * Specra — a role only ever means something inside a workspace, so the same person can own one and
 * merely read another, which is what a multi-tenant product needs and what a user-level role column
 * quietly makes impossible.
 *
 * <p>The reference to {@link User} points from {@code workspace} into {@code auth} and never back:
 * a user exists without a workspace (they have just registered), a workspace never exists without a
 * member.
 */
@Entity
@Table(name = "workspace_members")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class WorkspaceMember {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private WorkspaceRole role;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public static WorkspaceMember of(Workspace workspace, User user, WorkspaceRole role) {
    WorkspaceMember member = new WorkspaceMember();
    member.workspace = workspace;
    member.user = user;
    member.role = role;
    return member;
  }
}
