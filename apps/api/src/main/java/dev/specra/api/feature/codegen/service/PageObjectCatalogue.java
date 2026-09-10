package dev.specra.api.feature.codegen.service;

import dev.specra.api.feature.pageobject.service.PageObjectService;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The page objects a generation is given.
 *
 * <p>Which pages an IR needs is a question about the IR, and what is known about them is a question
 * for {@code feature/pageobject} — so this collects the first and asks for the second.
 *
 * <p>An uninspected page contributes nothing rather than a placeholder. That is what makes the
 * runner report its targets as unresolved, so the proposal arrives saying "inspect these pages
 * first" instead of shipping a guessed {@code #login-btn} — invariant 5, expressed as an absence.
 */
@Component
public class PageObjectCatalogue {

  private final PageObjectService pageObjects;

  public PageObjectCatalogue(PageObjectService pageObjects) {
    this.pageObjects = pageObjects;
  }

  public List<Map<String, Object>> forProject(UUID projectId, TestModelResponse model) {
    List<String> names = referencedPages(model);
    if (names.isEmpty()) {
      return List.of();
    }
    return pageObjects.forGeneration(projectId, names);
  }

  /** Every page the IR addresses, in the order it first mentions them. */
  static List<String> referencedPages(TestModelResponse model) {
    return model.document().referencedPages();
  }
}
