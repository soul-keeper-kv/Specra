package dev.specra.api.feature.workspace.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

  /**
   * The authorisation lookup, on the hot path of every workspace-scoped request. It selects the
   * role rather than the row: the decision needs one column, and loading the entity would drag two
   * lazy proxies along for what is ultimately a boolean.
   */
  @Query(
      """
      select m.role from WorkspaceMember m
      where m.workspace.id = :workspaceId and m.user.id = :userId
      """)
  Optional<WorkspaceRole> findRole(
      @Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

  /** The member list. The graph is what keeps the display name of each person off an N+1. */
  @EntityGraph(attributePaths = "user")
  Page<WorkspaceMember> findByWorkspaceId(UUID workspaceId, Pageable pageable);

  /** "My workspaces" — the only listing a tenant-scoped API may serve. */
  @EntityGraph(attributePaths = "workspace")
  Page<WorkspaceMember> findByUserId(UUID userId, Pageable pageable);

  @EntityGraph(attributePaths = "user")
  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  /** For the cross-tenant queries the assistant makes, which have to be narrowed to these ids. */
  @Query("select m.workspace.id from WorkspaceMember m where m.user.id = :userId")
  List<UUID> findWorkspaceIdsOf(@Param("userId") UUID userId);

  /**
   * How many people hold this role. What "you cannot remove the last owner" is checked with: a
   * workspace whose only owner leaves is a workspace nobody can administer again.
   */
  long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
