package dev.specra.api.feature.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.core.security.Permission;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * What each role may do, pinned down.
 *
 * <p>The mapping is the product decision this whole feature turns on, and it is the kind of thing a
 * one-line edit silently widens. These assertions are deliberately about the boundaries — what a
 * role must <em>not</em> have — because an accidental grant is the failure that never announces
 * itself.
 */
class WorkspaceRoleTest {

  @Test
  void anOwnerCanDoEverythingThereIs() {
    assertThat(WorkspaceRole.OWNER.permissions()).containsExactlyInAnyOrder(Permission.values());
  }

  /**
   * The one thing an administrator cannot do. An administrator who can delete the tenant is an
   * administrator whose mistake takes the projects, the test cases and the history with it.
   */
  @Test
  void anAdministratorCanDoEverythingExceptDeleteTheWorkspace() {
    assertThat(WorkspaceRole.ADMIN.can(Permission.WORKSPACE_DELETE)).isFalse();
    assertThat(WorkspaceRole.ADMIN.permissions())
        .containsAll(
            Arrays.stream(Permission.values())
                .filter(permission -> permission != Permission.WORKSPACE_DELETE)
                .toList());
  }

  @Test
  void aMemberReadsAndWritesContentButAdministersNothing() {
    assertThat(WorkspaceRole.MEMBER.can(Permission.CONTENT_VIEW)).isTrue();
    assertThat(WorkspaceRole.MEMBER.can(Permission.CONTENT_EDIT)).isTrue();
    assertThat(WorkspaceRole.MEMBER.can(Permission.MEMBER_VIEW)).isTrue();

    assertThat(WorkspaceRole.MEMBER.can(Permission.CONTENT_DELETE)).isFalse();
    assertThat(WorkspaceRole.MEMBER.can(Permission.MEMBER_ADD)).isFalse();
    assertThat(WorkspaceRole.MEMBER.can(Permission.MEMBER_UPDATE_ROLE)).isFalse();
    assertThat(WorkspaceRole.MEMBER.can(Permission.MEMBER_REMOVE)).isFalse();
    assertThat(WorkspaceRole.MEMBER.can(Permission.WORKSPACE_UPDATE)).isFalse();
    assertThat(WorkspaceRole.MEMBER.can(Permission.WORKSPACE_DELETE)).isFalse();
  }

  /** Every member can see the workspace they are in; that is what membership means. */
  @Test
  void everyRoleCanSeeTheWorkspace() {
    for (WorkspaceRole role : WorkspaceRole.values()) {
      assertThat(role.can(Permission.WORKSPACE_VIEW)).as("%s", role).isTrue();
    }
  }

  /**
   * Seniority comes from the declaration order, which is what {@code WorkspaceMemberService} reads
   * when it refuses to let an administrator act on an owner.
   */
  @Test
  void seniorityFollowsTheDeclarationOrder() {
    assertThat(WorkspaceRole.OWNER.outranks(WorkspaceRole.ADMIN)).isTrue();
    assertThat(WorkspaceRole.ADMIN.outranks(WorkspaceRole.MEMBER)).isTrue();
    assertThat(WorkspaceRole.ADMIN.outranks(WorkspaceRole.OWNER)).isFalse();

    // Strict: two administrators cannot act on each other, which is what stops a mutual demotion.
    assertThat(WorkspaceRole.ADMIN.outranks(WorkspaceRole.ADMIN)).isFalse();
    assertThat(WorkspaceRole.ADMIN.outranksOrEquals(WorkspaceRole.ADMIN)).isTrue();
  }

  /** The slug is on the wire and in the message bundle, so it must not drift from the name. */
  @Test
  void theSlugIsTheLowercasedName() {
    assertThat(WorkspaceRole.OWNER.slug()).isEqualTo("owner");
    assertThat(WorkspaceRole.OWNER.messageKey()).isEqualTo("role.owner");
    assertThat(Permission.MEMBER_UPDATE_ROLE.slug()).isEqualTo("member-update-role");
    assertThat(Permission.MEMBER_UPDATE_ROLE.messageKey())
        .isEqualTo("permission.member-update-role");
  }
}
