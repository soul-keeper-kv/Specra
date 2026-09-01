package dev.specra.api.feature.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ForbiddenException;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.service.AuthService;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceMember;
import dev.specra.api.feature.workspace.domain.WorkspaceMemberRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.MemberAddRequest;
import dev.specra.api.feature.workspace.dto.MemberRoleRequest;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapperImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The four rules that stop a workspace from being captured or left unadministrable.
 *
 * <p>Each of these is a way an installation could be broken by an ordinary-looking request, and
 * none of them is expressible as a permission: they are all about the relationship between the
 * caller's role and the target's, which only this service can see.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceMemberServiceTest {

  private static final UUID WORKSPACE = UUID.randomUUID();
  private static final UUID CALLER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  @Mock WorkspaceMemberRepository members;
  @Mock WorkspaceRepository workspaces;
  @Mock WorkspaceAccess access;
  @Mock AuthService users;

  WorkspaceMemberService service;

  @BeforeEach
  void setUp() {
    service =
        new WorkspaceMemberService(members, workspaces, new WorkspaceMapperImpl(), access, users);

    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(CALLER, "admin@specra.dev", "Admin"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  /** Otherwise an administrator promotes themselves and the two roles mean the same thing. */
  @Test
  void nobodyMayChangeTheirOwnRole() {
    signedInAs(WorkspaceRole.ADMIN);
    memberExists(CALLER, WorkspaceRole.ADMIN);

    assertThatThrownBy(
            () -> service.changeRole(WORKSPACE, CALLER, new MemberRoleRequest(WorkspaceRole.OWNER)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("error.member.cannot-change-own-role");

    verify(members, never()).save(any());
  }

  /** The same takeover by another route: demote the owner, then there is only you. */
  @Test
  void anAdministratorMayNotActOnAnOwner() {
    signedInAs(WorkspaceRole.ADMIN);
    memberExists(OTHER, WorkspaceRole.OWNER);

    assertThatThrownBy(
            () -> service.changeRole(WORKSPACE, OTHER, new MemberRoleRequest(WorkspaceRole.MEMBER)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("error.member.outranked");
  }

  /**
   * Peers may act on each other — an administrator can remove another administrator, and an owner
   * can demote another owner to hand the workspace over. What keeps that from being a coup is the
   * rule above it (nobody edits their own row) and the one below (the last owner stays).
   */
  @Test
  void peersMayActOnEachOther() {
    signedInAs(WorkspaceRole.ADMIN);
    memberExists(OTHER, WorkspaceRole.ADMIN);

    service.remove(WORKSPACE, OTHER);

    verify(members).delete(any(WorkspaceMember.class));
  }

  @Test
  void aRoleAboveYourOwnCannotBeGranted() {
    signedInAs(WorkspaceRole.ADMIN);
    when(users.requireByEmail("new@specra.dev")).thenReturn(userWithId(OTHER));

    assertThatThrownBy(
            () ->
                service.add(WORKSPACE, new MemberAddRequest("new@specra.dev", WorkspaceRole.OWNER)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("error.member.cannot-grant");
  }

  /**
   * A workspace with no owner is one nobody can ever administer again, and no endpoint undoes it.
   */
  @Test
  void theLastOwnerCannotBeDemoted() {
    signedInAs(WorkspaceRole.OWNER);
    memberExists(OTHER, WorkspaceRole.OWNER);
    when(members.countByWorkspaceIdAndRole(WORKSPACE, WorkspaceRole.OWNER)).thenReturn(1L);

    assertThatThrownBy(
            () -> service.changeRole(WORKSPACE, OTHER, new MemberRoleRequest(WorkspaceRole.ADMIN)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.member.last-owner");
  }

  @Test
  void theLastOwnerCannotLeaveEither() {
    memberExists(CALLER, WorkspaceRole.OWNER);
    when(members.countByWorkspaceIdAndRole(WORKSPACE, WorkspaceRole.OWNER)).thenReturn(1L);

    assertThatThrownBy(() -> service.leave(WORKSPACE))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.member.last-owner");
  }

  /** With a second owner in place, the first is free to go. */
  @Test
  void anOwnerMayLeaveOnceSomebodyElseOwnsItToo() {
    memberExists(CALLER, WorkspaceRole.OWNER);
    when(members.countByWorkspaceIdAndRole(WORKSPACE, WorkspaceRole.OWNER)).thenReturn(2L);

    service.leave(WORKSPACE);

    verify(members).delete(any(WorkspaceMember.class));
  }

  /** "Remove" is an administrative act on somebody else; leaving is what you do to yourself. */
  @Test
  void removingYourselfIsRefusedAndPointsAtLeaving() {
    signedInAs(WorkspaceRole.OWNER);
    memberExists(CALLER, WorkspaceRole.OWNER);

    assertThatThrownBy(() -> service.remove(WORKSPACE, CALLER))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("error.member.cannot-remove-self");
  }

  @Test
  void addingSomebodyWhoIsAlreadyHereIsAConflictRatherThanASecondRow() {
    signedInAs(WorkspaceRole.OWNER);
    when(users.requireByEmail("qa@specra.dev")).thenReturn(userWithId(OTHER));
    when(members.existsByWorkspaceIdAndUserId(WORKSPACE, OTHER)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.add(WORKSPACE, new MemberAddRequest("qa@specra.dev", WorkspaceRole.MEMBER)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.member.already-a-member");
  }

  @Test
  void anOwnerAddsAMember() {
    signedInAs(WorkspaceRole.OWNER);
    when(users.requireByEmail("qa@specra.dev")).thenReturn(userWithId(OTHER));
    when(members.existsByWorkspaceIdAndUserId(WORKSPACE, OTHER)).thenReturn(false);
    when(workspaces.findById(WORKSPACE)).thenReturn(Optional.of(new Workspace()));
    when(members.save(any(WorkspaceMember.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.add(WORKSPACE, new MemberAddRequest("qa@specra.dev", WorkspaceRole.MEMBER));

    assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
    assertThat(response.email()).isEqualTo("qa@specra.dev");
    assertThat(response.userId()).isEqualTo(OTHER);
  }

  private void signedInAs(WorkspaceRole role) {
    when(access.require(any(UUID.class), any(Permission.class))).thenReturn(role);
  }

  private void memberExists(UUID userId, WorkspaceRole role) {
    when(members.findByWorkspaceIdAndUserId(WORKSPACE, userId))
        .thenReturn(Optional.of(WorkspaceMember.of(new Workspace(), userWithId(userId), role)));
  }

  private static User userWithId(UUID id) {
    User user = new User();
    user.setId(id);
    user.setEmail("qa@specra.dev");
    user.setDisplayName("QA");
    return user;
  }
}
