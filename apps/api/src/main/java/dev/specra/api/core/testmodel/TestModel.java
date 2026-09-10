package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A test's intent, with no execution engine anywhere in it.
 *
 * <p>This is the abstraction the product turns on: understanding produces it, the adapter projects
 * it into source code, impact analysis diffs it, and failure analysis reads it. Nothing here names
 * Playwright, and nothing here holds a duration.
 *
 * <p>The schema in {@code packages/test-model} is the source of truth for the shape; this record is
 * a typed mirror of it. {@code TestModelContractTest} checks the two against the same fixtures, so
 * they cannot drift apart in silence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestModel(
    int irVersion,
    String name,
    String description,
    List<String> tags,
    List<TestParameter> parameters,
    List<TestStep> setup,
    List<TestStep> steps,
    List<TestStep> teardown) {

  /**
   * The only version this codebase writes. Reading an older one is a migrator's job, not a cast.
   */
  public static final int CURRENT_IR_VERSION = 1;

  public TestModel {
    tags = tags == null ? List.of() : List.copyOf(tags);
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    setup = setup == null ? List.of() : List.copyOf(setup);
    steps = steps == null ? List.of() : List.copyOf(steps);
    teardown = teardown == null ? List.of() : List.copyOf(teardown);
  }

  /** Setup, steps and teardown in the order they run. */
  public Stream<TestStep> allSteps() {
    return Stream.of(setup, steps, teardown).flatMap(List::stream);
  }

  /**
   * Every page this model addresses, in the order it first mentions them.
   *
   * <p>A question about an IR and nothing else, which is why it lives here rather than in whichever
   * feature happens to ask. Two do: a generation needs the page objects for the code it writes, and
   * an inspection needs them for the prelude it replays — and a feature reaching into another
   * feature for this would be the cycle {@code ArchitectureTest} forbids.
   */
  public List<String> referencedPages() {
    Map<String, Boolean> seen = new LinkedHashMap<>();
    allSteps()
        .forEach(
            step -> {
              collectPage(step.target(), seen);
              collectPage(step.to(), seen);
            });
    return List.copyOf(seen.keySet());
  }

  private static void collectPage(Target target, Map<String, Boolean> seen) {
    if (target != null && target.isPage()) {
      seen.putIfAbsent(target.page(), Boolean.TRUE);
    }
  }
}
