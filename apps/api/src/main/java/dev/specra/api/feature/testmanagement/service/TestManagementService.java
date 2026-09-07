package dev.specra.api.feature.testmanagement.service;

import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.domain.TestCasePriority;
import dev.specra.api.feature.testcase.dto.TestCaseRequest;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepRequest;
import dev.specra.api.feature.testcase.service.TestCaseService;
import dev.specra.api.feature.testmanagement.domain.TestManagementBinding;
import dev.specra.api.feature.testmanagement.domain.TestManagementBindingRepository;
import dev.specra.api.feature.testmanagement.domain.TestManagementConnection;
import dev.specra.api.feature.testmanagement.dto.ExternalTestDetail;
import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.dto.TestManagementBindingRequest;
import dev.specra.api.feature.testmanagement.dto.TestManagementBindingResponse;
import dev.specra.api.feature.testmanagement.dto.TestManagementVerifyResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates provider-neutral bindings and delegates remote work through the registry. */
@Service
@Transactional(readOnly = true)
public class TestManagementService {
  private final TestManagementBindingRepository bindings;
  private final TestManagementConnectionService connections;
  private final ProjectService projects;
  private final TestCaseService testCases;

  public TestManagementService(
      TestManagementBindingRepository bindings,
      TestManagementConnectionService connections,
      ProjectService projects,
      TestCaseService testCases) {
    this.bindings = bindings;
    this.connections = connections;
    this.projects = projects;
    this.testCases = testCases;
  }

  public TestManagementBindingResponse getBinding(UUID projectId) {
    UUID workspaceId = context(projectId, Permission.CONTENT_VIEW);
    TestManagementBinding binding = requireBinding(projectId);
    return response(binding, connections.require(workspaceId, binding.getConnectionId()));
  }

  @Transactional
  public TestManagementBindingResponse bind(UUID projectId, TestManagementBindingRequest request) {
    UUID workspaceId = context(projectId, Permission.CONTENT_EDIT);
    TestManagementConnection connection = connections.require(workspaceId, request.connectionId());
    TestManagementBinding binding =
        bindings.findByProjectId(projectId).orElseGet(TestManagementBinding::new);
    if (binding.getId() == null) {
      binding.setWorkspaceId(workspaceId);
      binding.setProjectId(projectId);
    }
    binding.setConnectionId(connection.getId());
    binding.setRemoteProjectId(request.remoteProjectId().trim());
    return response(bindings.save(binding), connection);
  }

  @Transactional
  public void unbind(UUID projectId) {
    context(projectId, Permission.CONTENT_EDIT);
    bindings.delete(requireBinding(projectId));
  }

  public TestManagementVerifyResponse verify(UUID projectId) {
    BoundProvider bound = bound(projectId);
    return bound
        .material()
        .provider()
        .verify(bound.material().providerConnection(), bound.binding().getRemoteProjectId());
  }

  public PageResponse<ExternalTestSummary> tests(
      UUID projectId, String query, boolean advanced, int page, int size) {
    BoundProvider bound = bound(projectId);
    return bound
        .material()
        .provider()
        .tests(
            bound.material().providerConnection(),
            bound.binding().getRemoteProjectId(),
            query,
            advanced,
            page,
            size);
  }

  public ExternalTestDetail test(UUID projectId, String externalId) {
    BoundProvider bound = bound(projectId);
    return bound
        .material()
        .provider()
        .test(
            bound.material().providerConnection(),
            bound.binding().getRemoteProjectId(),
            externalId);
  }

  /**
   * The Specra case an external test was imported as, or 404: the workspace asks this to decide
   * whether "prepare automation" or "model this test" is the next step.
   */
  public TestCaseResponse importedTestCase(UUID projectId, String externalId) {
    BoundProvider bound = bound(projectId);
    TestCaseResponse imported =
        testCases.findImported(projectId, bound.material().provider().kind(), externalId);
    if (imported == null) {
      throw new ResourceNotFoundException("resource.testcase", externalId);
    }
    return imported;
  }

  @Transactional
  public TestCaseResponse importTest(UUID projectId, String externalId) {
    BoundProvider bound = bound(projectId);
    ExternalTestDetail external =
        bound
            .material()
            .provider()
            .test(
                bound.material().providerConnection(),
                bound.binding().getRemoteProjectId(),
                externalId);
    List<TestCaseStepRequest> steps =
        external.steps().stream()
            .map(
                step ->
                    new TestCaseStepRequest(
                        step.action(), emptyToNull(step.data()), emptyToNull(step.expected())))
            .toList();
    TestCaseRequest request =
        new TestCaseRequest(
            external.title(),
            external.description(),
            null,
            null,
            priority(external.priority()),
            steps,
            Set.copyOf(external.labels()));
    return testCases.importExternal(
        projectId,
        request,
        bound.material().provider().kind(),
        external.externalId(),
        external.url());
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static TestCasePriority priority(String value) {
    if (value == null) {
      return TestCasePriority.MEDIUM;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "highest", "critical", "blocker" -> TestCasePriority.CRITICAL;
      case "high" -> TestCasePriority.HIGH;
      case "low", "lowest", "minor", "trivial" -> TestCasePriority.LOW;
      default -> TestCasePriority.MEDIUM;
    };
  }

  private BoundProvider bound(UUID projectId) {
    UUID workspaceId = context(projectId, Permission.CONTENT_VIEW);
    TestManagementBinding binding = requireBinding(projectId);
    return new BoundProvider(binding, connections.material(workspaceId, binding.getConnectionId()));
  }

  private UUID context(UUID projectId, Permission permission) {
    projects.requireAccess(projectId, permission);
    return projects.workspaceOf(projectId);
  }

  private TestManagementBinding requireBinding(UUID projectId) {
    return bindings
        .findByProjectId(projectId)
        .orElseThrow(
            () -> new ResourceNotFoundException("resource.test-management-binding", projectId));
  }

  private static TestManagementBindingResponse response(
      TestManagementBinding binding, TestManagementConnection connection) {
    return new TestManagementBindingResponse(
        binding.getId(),
        connection.getId(),
        connection.getName(),
        connection.getProvider(),
        Map.copyOf(connection.getConfiguration()),
        binding.getRemoteProjectId(),
        binding.getUpdatedAt());
  }

  private record BoundProvider(
      TestManagementBinding binding, TestManagementConnectionService.ConnectionMaterial material) {}
}
