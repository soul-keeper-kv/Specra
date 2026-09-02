package dev.specra.api.feature.run.web;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.run.dto.RunRequest;
import dev.specra.api.feature.run.dto.RunResponse;
import dev.specra.api.feature.run.service.RunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs: asked for, listed, read and cancelled.
 *
 * <p>Requesting one answers **202** with the queued run rather than 200 with a finished one. A
 * suite takes minutes; holding the request open would tie a thread to a browser and leave the user
 * with a spinner instead of a run they can navigate away from and come back to.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Runs", description = "Executing committed tests, and the evidence they produce.")
public class RunController {

  private final RunService service;

  public RunController(RunService service) {
    this.service = service;
  }

  @GetMapping("/projects/{projectId}/runs")
  @Operation(summary = "A project's runs, most recent first")
  public PageResponse<RunResponse> list(
      @PathVariable UUID projectId, @PageableDefault(size = 20) Pageable pageable) {
    return service.list(projectId, pageable);
  }

  @PostMapping("/projects/{projectId}/runs")
  @Operation(
      summary =
          "Queue a run and answer 202 with it. The commit sha is resolved now, so the run"
              + " records the tree the user was looking at rather than whatever the branch"
              + " moved to before a worker was free.")
  public ResponseEntity<RunResponse> request(
      @PathVariable UUID projectId, @Valid @RequestBody(required = false) RunRequest request) {
    RunResponse queued =
        service.request(projectId, request == null ? new RunRequest(null, null, null) : request);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/runs/" + queued.id()))
        .body(queued);
  }

  @GetMapping("/runs/{id}")
  @Operation(summary = "One run, with every matrix cell and the evidence each produced")
  public RunResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/runs/{id}/cancel")
  @Operation(
      summary =
          "Cancel a run. A queued one never starts; a running one stops at its next checkpoint.")
  public RunResponse cancel(@PathVariable UUID id) {
    return service.cancel(id);
  }
}
