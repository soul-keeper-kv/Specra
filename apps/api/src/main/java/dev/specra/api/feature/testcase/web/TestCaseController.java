package dev.specra.api.feature.testcase.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testcase.dto.TestCaseRequest;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseSummaryResponse;
import dev.specra.api.feature.testcase.service.TestCaseIndexService;
import dev.specra.api.feature.testcase.service.TestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Same two path shapes as projects: created and listed under the parent, addressed by id after. */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Test cases",
    description =
        "The manual case: intent in human language, versioned by Git later, never guessed.")
public class TestCaseController {

  private final TestCaseService service;
  private final TestCaseIndexService indexService;

  public TestCaseController(TestCaseService service, TestCaseIndexService indexService) {
    this.service = service;
    this.indexService = indexService;
  }

  @GetMapping("/projects/{projectId}/test-cases")
  @Operation(summary = "List a project's test cases, most recently updated first")
  public PageResponse<TestCaseSummaryResponse> list(
      @PathVariable UUID projectId,
      @Parameter(description = "Matches title or reference, case-insensitive")
          @RequestParam(required = false)
          String q,
      // A String, not the domain enum: the web layer stays out of domain/, and the service turns
      // an unknown value into the same invalid-parameter problem a bad number would produce.
      @Parameter(description = "NOT_AUTOMATED · MODELLED · GENERATED · COMMITTED")
          @RequestParam(required = false)
          String status,
      @Parameter(description = "Exact tag match") @RequestParam(required = false) String tag,
      @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.search(projectId, q, status, tag, pageable);
  }

  @PostMapping("/projects/{projectId}/test-cases")
  @Operation(summary = "Create a test case; its TC-n reference is minted from the project")
  public ResponseEntity<TestCaseResponse> create(
      @PathVariable UUID projectId, @Valid @RequestBody TestCaseRequest request) {
    TestCaseResponse created = service.create(projectId, request);
    return ResponseEntity.created(URI.create("/api/v1/test-cases/" + created.id())).body(created);
  }

  @GetMapping("/test-cases/{id}")
  @Operation(summary = "Fetch one test case with its steps")
  public TestCaseResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/test-cases/{id}")
  @Operation(
      summary =
          "Replace a test case; marks it out of date when an IR was already generated from it")
  public TestCaseResponse update(
      @PathVariable UUID id, @Valid @RequestBody TestCaseRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/test-cases/{id}")
  @Operation(summary = "Delete a test case; its embeddings are dropped after the commit")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test-cases/{id}/index")
  @Operation(summary = "Chunk, embed and store this case in pgvector so the assistant can cite it")
  public TestCaseResponse index(@PathVariable UUID id) {
    return indexService.index(id);
  }
}
