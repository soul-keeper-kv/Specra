package dev.specra.api.feature.analysis.web;

import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.feature.analysis.dto.FailureAnalysisResponse;
import dev.specra.api.feature.analysis.service.FailureAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role 5: what the evidence says about one failed matrix cell.
 *
 * <p>Addressed by run item rather than by run, because a run can fail in three browsers for three
 * different reasons and one verdict for all of them would be a worse answer than none.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Failure analysis",
    description = "Why a test failed, read from its evidence. Never weakens an assertion.")
public class FailureAnalysisController {

  private final FailureAnalysisService service;

  public FailureAnalysisController(FailureAnalysisService service) {
    this.service = service;
  }

  @GetMapping("/run-items/{id}/analysis")
  @Operation(summary = "The stored analysis for this cell, if one has been made")
  public FailureAnalysisResponse get(@PathVariable UUID id) {
    return service
        .find(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.failure-analysis", id));
  }

  @PostMapping("/run-items/{id}/analysis")
  @Operation(
      summary =
          "Analyse this failure, or return the reading it already has. `reanalyse=true` forces a"
              + " fresh call; without it a second press costs nothing, because the evidence"
              + " cannot change once the run has finished.")
  public FailureAnalysisResponse analyse(
      @PathVariable UUID id,
      @RequestParam(name = "reanalyse", defaultValue = "false") boolean reanalyse) {
    return service.analyse(id, reanalyse);
  }
}
