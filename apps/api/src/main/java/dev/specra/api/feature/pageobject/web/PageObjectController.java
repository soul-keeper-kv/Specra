package dev.specra.api.feature.pageobject.web;

import dev.specra.api.feature.pageobject.dto.ElementUpdateRequest;
import dev.specra.api.feature.pageobject.dto.InspectRequest;
import dev.specra.api.feature.pageobject.dto.PageObjectResponse;
import dev.specra.api.feature.pageobject.service.PageObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the application's pages contain, read rather than guessed.
 *
 * <p>Deliberately not paged: a project has a handful of pages, and the list is what the generation
 * screen shows beside "these need inspecting".
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Page objects",
    description = "Inspected pages and their locators. A locator is read, never invented.")
public class PageObjectController {

  private final PageObjectService service;

  public PageObjectController(PageObjectService service) {
    this.service = service;
  }

  @GetMapping("/projects/{projectId}/pages")
  @Operation(summary = "The project's page objects, inspected or not")
  public List<PageObjectResponse> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping("/projects/{projectId}/pages/inspect")
  @Operation(
      summary =
          "Open a page and record how its elements can be reached. Re-inspecting replaces the"
              + " elements rather than merging, so a removed element does not linger.")
  public PageObjectResponse inspect(
      @PathVariable UUID projectId, @Valid @RequestBody InspectRequest request) {
    return service.inspect(projectId, request);
  }

  @GetMapping("/pages/{id}")
  @Operation(summary = "One page object")
  public PageObjectResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/pages/{id}/elements/{name}")
  @Operation(
      summary =
          "Correct one element's locator. Inspection is a good reading of a real page, not an"
              + " oracle — the person who knows the application overrules it here.")
  public PageObjectResponse updateElement(
      @PathVariable UUID id,
      @PathVariable String name,
      @Valid @RequestBody ElementUpdateRequest request) {
    return service.updateElement(
        id, name, request.strategy(), request.value(), request.qualifier());
  }
}
