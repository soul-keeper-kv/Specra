package dev.specra.api.feature.testmanagement.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testmanagement.dto.ExternalTestDetail;
import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.dto.TestManagementBindingRequest;
import dev.specra.api.feature.testmanagement.dto.TestManagementBindingResponse;
import dev.specra.api.feature.testmanagement.dto.TestManagementVerifyResponse;
import dev.specra.api.feature.testmanagement.service.TestManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/projects/{projectId}/test-management")
@Tag(name = "Test management", description = "Bind and consume an external test system.")
public class TestManagementController {
  private final TestManagementService service;

  public TestManagementController(TestManagementService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Get the project's test-management binding")
  public TestManagementBindingResponse get(@PathVariable UUID projectId) {
    return service.getBinding(projectId);
  }

  @PutMapping
  @Operation(summary = "Create or replace the project's test-management binding")
  public TestManagementBindingResponse bind(
      @PathVariable UUID projectId, @Valid @RequestBody TestManagementBindingRequest request) {
    return service.bind(projectId, request);
  }

  @DeleteMapping
  @Operation(summary = "Remove the binding without changing remote data")
  public ResponseEntity<Void> unbind(@PathVariable UUID projectId) {
    service.unbind(projectId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/verify")
  @Operation(summary = "Verify credentials, project access and test discovery")
  public TestManagementVerifyResponse verify(@PathVariable UUID projectId) {
    return service.verify(projectId);
  }

  @GetMapping("/tests")
  @Operation(summary = "Search external tests without copying ownership into Specra")
  public PageResponse<ExternalTestSummary> tests(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "false") boolean advanced,
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.page.min}") int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "{validation.page-size.min}") @Max(value = 100, message = "{validation.page-size.max}") int size) {
    return service.tests(projectId, q, advanced, page, size);
  }

  @GetMapping("/tests/{externalId}")
  @Operation(summary = "Get an external test with its manual steps")
  public ExternalTestDetail test(@PathVariable UUID projectId, @PathVariable String externalId) {
    return service.test(projectId, externalId);
  }

  @GetMapping("/tests/{externalId}/test-case")
  @Operation(summary = "The Specra test case this external test was imported as, or 404")
  public TestCaseResponse importedTestCase(
      @PathVariable UUID projectId, @PathVariable String externalId) {
    return service.importedTestCase(projectId, externalId);
  }

  @PostMapping("/tests/{externalId}/import")
  @Operation(summary = "Import and link an external test as an automation input")
  public TestCaseResponse importTest(
      @PathVariable UUID projectId, @PathVariable String externalId) {
    return service.importTest(projectId, externalId);
  }
}
