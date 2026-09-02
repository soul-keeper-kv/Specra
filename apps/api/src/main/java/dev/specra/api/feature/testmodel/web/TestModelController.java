package dev.specra.api.feature.testmodel.web;

import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import dev.specra.api.feature.testmodel.dto.TestModelVersionResponse;
import dev.specra.api.feature.testmodel.service.TestModelStore;
import dev.specra.api.feature.testmodel.service.TestModellingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The IR under its test case. {@code POST} answers synchronously with the stored version rather
 * than the 202-plus-stream 05-api-contracts.md sketches: one model call is seconds, and a reviewer
 * wants the document, not a ticket for it. The streaming form is a later addition, not a change.
 */
@RestController
@RequestMapping("/api/v1/test-cases/{id}/model")
@Tag(
    name = "Test models",
    description =
        "The Test Model: the case's intent as a structure, reviewed before any code exists.")
public class TestModelController {

  private final TestModellingService modelling;
  private final TestModelStore store;

  public TestModelController(TestModellingService modelling, TestModelStore store) {
    this.modelling = modelling;
    this.store = store;
  }

  @PostMapping
  @Operation(
      summary =
          "Derive a Test Model from the manual case; stores the next version and marks the case"
              + " MODELLED. 422 test-case-ambiguous carries the questions, 422 test-model-invalid"
              + " the violations.")
  public TestModelResponse model(@PathVariable UUID id) {
    return modelling.model(id);
  }

  @GetMapping
  @Operation(summary = "The current Test Model, with its pages and manual-step coverage")
  public TestModelResponse current(@PathVariable UUID id) {
    return store.current(id);
  }

  @GetMapping("/versions")
  @Operation(summary = "Every version of this case's Test Model, newest first")
  public List<TestModelVersionResponse> versions(@PathVariable UUID id) {
    return store.versions(id);
  }

  @GetMapping("/versions/{version}")
  @Operation(summary = "One earlier version of the Test Model")
  public TestModelResponse version(@PathVariable UUID id, @PathVariable int version) {
    return store.version(id, version);
  }
}
