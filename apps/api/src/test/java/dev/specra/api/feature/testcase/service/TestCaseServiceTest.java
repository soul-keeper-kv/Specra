package dev.specra.api.feature.testcase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.domain.AutomationStatus;
import dev.specra.api.feature.testcase.domain.TestCase;
import dev.specra.api.feature.testcase.domain.TestCaseRepository;
import dev.specra.api.feature.testcase.dto.TestCaseRequest;
import dev.specra.api.feature.testcase.dto.TestCaseStepRequest;
import dev.specra.api.feature.testcase.mapper.TestCaseMapperImpl;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID WORKSPACE = UUID.randomUUID();

  @Mock TestCaseRepository repository;
  @Mock ProjectService projects;
  @Mock WorkspaceService workspaces;

  /** Recorded rather than mocked: the assertions are about what was announced, not how. */
  final List<Object> published = new ArrayList<>();

  TestCaseService service;

  @BeforeEach
  void setUp() {
    service =
        new TestCaseService(
            repository, new TestCaseMapperImpl(), projects, workspaces, published::add);
  }

  @Test
  void createMintsTheReferenceAndNumbersTheStepsFromOne() {
    when(projects.workspaceOf(PROJECT)).thenReturn(WORKSPACE);
    when(projects.nextTestCaseReference(PROJECT)).thenReturn("TC-1");
    saveReturnsWhatItWasGiven();

    var response =
        service.create(
            PROJECT,
            request(
                "Đăng nhập hợp lệ",
                List.of(
                    new TestCaseStepRequest("Mở trang đăng nhập", null),
                    new TestCaseStepRequest("Nhập email và mật khẩu", "Nút Đăng nhập bật"))));

    assertThat(response.reference()).isEqualTo("TC-1");
    assertThat(response.automationStatus()).isEqualTo(AutomationStatus.NOT_AUTOMATED);
    assertThat(response.steps()).hasSize(2);
    assertThat(response.steps().get(0).position()).isEqualTo(1);
    assertThat(response.steps().get(1).position()).isEqualTo(2);
    assertThat(response.steps().get(1).expected()).isEqualTo("Nút Đăng nhập bật");
  }

  /** Editing a case nothing was generated from is just editing; there is nothing to be behind. */
  @Test
  void editingANotAutomatedCaseDoesNotMarkItOutOfDate() {
    TestCase existing = existingCase(AutomationStatus.NOT_AUTOMATED);
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    saveReturnsWhatItWasGiven();

    var response = service.update(id, request("New title", List.of()));

    assertThat(response.outOfDate()).isFalse();
    assertThat(response.title()).isEqualTo("New title");
  }

  /**
   * The flag, not a status: the case went backwards relative to its IR but must not forget how far
   * down the pipeline it already was.
   */
  @Test
  void editingAModelledCaseMarksItOutOfDateAndUnIndexed() {
    TestCase existing = existingCase(AutomationStatus.MODELLED);
    existing.setIndexedAt(Instant.now());
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    saveReturnsWhatItWasGiven();

    var response = service.update(id, request("Edited", List.of()));

    assertThat(response.outOfDate()).isTrue();
    assertThat(response.automationStatus()).isEqualTo(AutomationStatus.MODELLED);
    assertThat(response.indexedAt()).isNull();
    assertThat(published).singleElement().isInstanceOf(TestCaseEvents.TestCaseContentChanged.class);
  }

  @Test
  void anEditRenumbersTheStepsDensely() {
    TestCase existing = existingCase(AutomationStatus.NOT_AUTOMATED);
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.of(existing));
    saveReturnsWhatItWasGiven();

    var response =
        service.update(
            id,
            request("Edited", List.of(new TestCaseStepRequest("Only step left", "Still works"))));

    assertThat(response.steps()).hasSize(1);
    assertThat(response.steps().get(0).position()).isEqualTo(1);
  }

  private static TestCase existingCase(AutomationStatus status) {
    TestCase testCase = new TestCase();
    testCase.setWorkspaceId(WORKSPACE);
    testCase.setProjectId(PROJECT);
    testCase.setReference("TC-7");
    testCase.setTitle("Old");
    testCase.setAutomationStatus(status);
    return testCase;
  }

  private static TestCaseRequest request(String title, List<TestCaseStepRequest> steps) {
    return new TestCaseRequest(title, null, null, null, null, steps, Set.of());
  }

  private void saveReturnsWhatItWasGiven() {
    when(repository.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
  }
}
