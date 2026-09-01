package dev.specra.api.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.feature.project.domain.AutomationEngine;
import dev.specra.api.feature.project.domain.Project;
import dev.specra.api.feature.project.domain.ProjectRepository;
import dev.specra.api.feature.project.dto.ProjectPatchRequest;
import dev.specra.api.feature.project.dto.ProjectRequest;
import dev.specra.api.feature.project.mapper.ProjectMapperImpl;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  private static final UUID WORKSPACE = UUID.randomUUID();

  @Mock ProjectRepository repository;
  @Mock WorkspaceService workspaces;

  ProjectService service;

  @BeforeEach
  void setUp() {
    service = new ProjectService(repository, new ProjectMapperImpl(), workspaces);
  }

  @Test
  void derivesTheKeyFromTheFirstWordOfTheName() {
    saveReturnsWhatItWasGiven();
    when(repository.existsByWorkspaceIdAndKey(eq(WORKSPACE), any())).thenReturn(false);

    var response = service.create(WORKSPACE, new ProjectRequest("Kiểm thử Acme", null, null, null));

    assertThat(response.key()).isEqualTo("KIEM");
    assertThat(response.workspaceId()).isEqualTo(WORKSPACE);
    assertThat(response.engine()).isEqualTo(AutomationEngine.PLAYWRIGHT);
  }

  @Test
  void suffixesADerivedKeyThatIsAlreadyTaken() {
    saveReturnsWhatItWasGiven();
    when(repository.existsByWorkspaceIdAndKey(WORKSPACE, "ACME")).thenReturn(true);
    when(repository.existsByWorkspaceIdAndKey(WORKSPACE, "ACME2")).thenReturn(false);

    assertThat(service.create(WORKSPACE, new ProjectRequest("Acme shop", null, null, null)).key())
        .isEqualTo("ACME2");
  }

  @Test
  void reportsAConflictWhenTheCallerChoseATakenKey() {
    when(repository.existsByWorkspaceIdAndKey(WORKSPACE, "ACME")).thenReturn(true);

    assertThatThrownBy(
            () -> service.create(WORKSPACE, new ProjectRequest("Acme shop", "ACME", null, null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("error.project.duplicate-key");
  }

  /**
   * The parent is checked through the workspace feature's service rather than its repository, so
   * the 404 is worded once. Nothing is written when it fails.
   */
  @Test
  void refusesToCreateAProjectInAWorkspaceThatDoesNotExist() {
    doThrow(new ResourceNotFoundException("resource.workspace", WORKSPACE))
        .when(workspaces)
        .requireExists(WORKSPACE);

    assertThatThrownBy(
            () -> service.create(WORKSPACE, new ProjectRequest("Acme shop", null, null, null)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aPatchLeavesTheFieldsItDoesNotCarryAlone() {
    Project existing = new Project();
    existing.setWorkspaceId(WORKSPACE);
    existing.setKey("ACME");
    existing.setName("Acme shop");
    existing.setDescription("Checkout journeys");
    UUID id = UUID.randomUUID();

    when(repository.findById(id)).thenReturn(Optional.of(existing));
    saveReturnsWhatItWasGiven();

    var response = service.update(id, new ProjectPatchRequest("Acme storefront", null));

    assertThat(response.name()).isEqualTo("Acme storefront");
    assertThat(response.description()).isEqualTo("Checkout journeys");
    assertThat(response.key()).isEqualTo("ACME");
  }

  private void saveReturnsWhatItWasGiven() {
    when(repository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
  }
}
