package dev.specra.api.feature.codegen.web;

import dev.specra.api.feature.codegen.dto.ApplyGenerationRequest;
import dev.specra.api.feature.codegen.dto.CodeGenerationResponse;
import dev.specra.api.feature.codegen.service.CodeGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Code proposals: make one, look at it, accept or reject it.
 *
 * <p>The two verbs are deliberately separate endpoints. There is no call that generates and
 * applies, because the human decision between them is the product's central rule and an endpoint
 * that skipped it would make the rule optional.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Code generation",
    description = "The IR projected into Playwright source: proposed, reviewed, then committed.")
public class CodeGenerationController {

  private final CodeGenerationService service;

  public CodeGenerationController(CodeGenerationService service) {
    this.service = service;
  }

  @PostMapping("/test-cases/{id}/code")
  @Operation(
      summary =
          "Project the case's current Test Model into files and store them as a proposal."
              + " Writes nothing to the repository.")
  public CodeGenerationResponse generate(@PathVariable UUID id) {
    return service.generate(id);
  }

  @GetMapping("/test-cases/{id}/code")
  @Operation(summary = "The proposal awaiting review, with each file's current contents beside it")
  public CodeGenerationResponse current(@PathVariable UUID id) {
    return service.current(id);
  }

  @GetMapping("/test-cases/{id}/code/history")
  @Operation(summary = "Every generation for this case, newest first")
  public List<CodeGenerationResponse> history(@PathVariable UUID id) {
    return service.history(id);
  }

  @PostMapping("/code-generations/{id}/apply")
  @Operation(
      summary =
          "Accept a proposal: write its files into the working copy and commit them as the"
              + " signed-in user. The only endpoint that puts generated code in a repository.")
  public CodeGenerationResponse apply(
      @PathVariable UUID id, @Valid @RequestBody(required = false) ApplyGenerationRequest request) {
    return service.apply(id, request);
  }

  @PostMapping("/code-generations/{id}/reject")
  @Operation(summary = "Reject a proposal; nothing is written and the case keeps its status")
  public CodeGenerationResponse reject(@PathVariable UUID id) {
    return service.reject(id);
  }
}
