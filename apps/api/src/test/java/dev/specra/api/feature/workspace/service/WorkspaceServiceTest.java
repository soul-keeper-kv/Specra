package dev.specra.api.feature.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.feature.workspace.domain.Workspace;
import dev.specra.api.feature.workspace.domain.WorkspaceRepository;
import dev.specra.api.feature.workspace.dto.WorkspaceRequest;
import dev.specra.api.feature.workspace.mapper.WorkspaceMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

  @Mock WorkspaceRepository repository;

  WorkspaceService service;

  @BeforeEach
  void setUp() {
    // The generated MapStruct implementation, not a mock: mapping bugs should fail here.
    service = new WorkspaceService(repository, new WorkspaceMapperImpl());
  }

  @Test
  void derivesASlugFromTheNameWhenNoneIsGiven() {
    saveReturnsWhatItWasGiven();
    when(repository.existsBySlug(any())).thenReturn(false);

    var response = service.create(new WorkspaceRequest("Kiểm thử Acme", null));

    assertThat(response.slug()).isEqualTo("kiem-thu-acme");
    assertThat(response.name()).isEqualTo("Kiểm thử Acme");
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
