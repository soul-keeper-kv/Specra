package dev.specra.api.feature.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.service.AuthService;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceMember;
import dev.specra.api.feature.workspace.domain.WorkspaceMemberRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.domain.WorkspaceRole;
import dev.specra.api.feature.workspace.dto.WorkspaceRequest;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapperImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceServiceTest {

  private static final UUID CALLER = UUID.randomUUID();

  @Mock WorkspaceRepository repository;
  @Mock WorkspaceMemberRepository members;
  @Mock WorkspaceAccess access;
  @Mock AuthService users;

  WorkspaceService service;

  @BeforeEach
  void setUp() {
    // The generated MapStruct implementation, not a mock: mapping bugs should fail here.
    service = new WorkspaceService(repository, members, new WorkspaceMapperImpl(), access, users);

    User caller = new User();
    caller.setEmail("owner@specra.dev");
    caller.setDisplayName("Owner");
    when(users.require(CALLER)).thenReturn(caller);

    // Creating a workspace reads the caller off the security context rather than taking an id,
    // so the id can only ever be the authenticated one.
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(CALLER, "owner@specra.dev", "Owner"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  @AfterEach
  void clearContext() {
    // The holder is a thread local and the threads are pooled; a leak would sign the next test in.
    SecurityContextHolder.clearContext();
  }

  @Test
  void derivesASlugFromTheNameWhenNoneIsGiven() {
    saveReturnsWhatItWasGiven();
    when(repository.existsBySlug(any())).thenReturn(false);

    var response = service.create(new WorkspaceRequest("Kiểm thử Acme", null));

    assertThat(response.slug()).isEqualTo("kiem-thu-acme");
    assertThat(response.name()).isEqualTo("Kiểm thử Acme");
  }

  /**
   * The first membership is not a separate step a caller could forget: a workspace nobody owns is
   * unreachable the moment it exists, because every read goes through a membership.
   */
  @Test
  void makesWhoeverCreatedItTheOwner() {
    saveReturnsWhatItWasGiven();
    when(repository.existsBySlug(any())).thenReturn(false);

    var response = service.create(new WorkspaceRequest("Acme QA", null));

    ArgumentCaptor<WorkspaceMember> saved = ArgumentCaptor.forClass(WorkspaceMember.class);
    verify(members).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
    assertThat(saved.getValue().getUser().getDisplayName()).isEqualTo("Owner");
    assertThat(response.role()).isEqualTo(WorkspaceRole.OWNER);
  }

  /** A slug we invented is an implementation detail, so a clash is ours to resolve silently. */
  @Test
  void suffixesADerivedSlugThatIsAlreadyTaken() {
    saveReturnsWhatItWasGiven();
    when(repository.existsBySlug("acme-qa")).thenReturn(true);
    when(repository.existsBySlug("acme-qa-2")).thenReturn(false);

    assertThat(service.create(new WorkspaceRequest("Acme QA", null)).slug()).isEqualTo("acme-qa-2");
  }

  /** A slug the caller chose is theirs, so silently changing it would be the wrong answer. */
  @Test
  void reportsAConflictWhenTheCallerChoseATakenSlug() {
    when(repository.existsBySlug("acme-qa")).thenReturn(true);

    assertThatThrownBy(() -> service.create(new WorkspaceRequest("Acme QA", "acme-qa")))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.workspace.duplicate-slug");
  }

  @Test
  void fallsBackWhenTheNameCarriesNothingASlugCanHold() {
    saveReturnsWhatItWasGiven();
    when(repository.existsBySlug(any())).thenReturn(false);

    assertThat(service.create(new WorkspaceRequest("字", null)).slug()).isEqualTo("workspace");
  }

  private void saveReturnsWhatItWasGiven() {
    when(repository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));
  }
}
