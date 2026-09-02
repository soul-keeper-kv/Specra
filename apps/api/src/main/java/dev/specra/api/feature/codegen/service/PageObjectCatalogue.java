package dev.specra.api.feature.codegen.service;

import dev.specra.api.core.testmodel.Target;
import dev.specra.api.feature.testmodel.dto.TestModelResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The page objects a generation is given.
 *
 * <p>Inspection is M8, so nothing has a real locator yet. Rather than inventing one — the single
 * largest source of flake in tools like this — this returns each referenced page with its elements
 * and <em>no</em> locators, which is exactly what makes the runner report them as unresolved. The
 * proposal then arrives saying "these pages need inspecting" instead of shipping a guessed {@code
 * #login-btn}.
 *
 * <p>When {@code page_objects} is populated (V5 already has the table), this class reads from it
 * and nothing above it changes.
 */
@Component
public class PageObjectCatalogue {

  public List<Map<String, Object>> forProject(UUID projectId, TestModelResponse model) {
    Map<String, List<String>> byPage = new LinkedHashMap<>();
    model
        .document()
        .allSteps()
        .forEach(
            step -> {
              collect(step.target(), byPage);
              collect(step.to(), byPage);
            });

    List<Map<String, Object>> pages = new ArrayList<>();
    byPage.forEach(
        (name, elements) -> pages.add(Map.of("name", name, "elements", elementsOf(elements))));
    return List.copyOf(pages);
  }

  /**
   * Deliberately empty: an element with no inspected locator cannot be projected, and the runner
   * says so. A placeholder locator here would compile and then fail against the real application,
   * which is worse than a refusal a person can act on.
   */
  private static List<Map<String, Object>> elementsOf(List<String> names) {
    return List.of();
  }

  private static void collect(Target target, Map<String, List<String>> byPage) {
    if (target == null || !target.isPage()) {
      return;
    }
    List<String> elements = byPage.computeIfAbsent(target.page(), key -> new ArrayList<>());
    if (target.element() != null && !elements.contains(target.element())) {
      elements.add(target.element());
    }
  }
}
