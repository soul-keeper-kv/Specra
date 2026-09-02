package dev.specra.api.core.testmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What changed between two versions of a Test Model, step by step.
 *
 * <p>08-ai-pipeline.md: "regeneration is a diff, not a rewrite". A test case whose one step went
 * from "Click Login" to "Click Sign in" should produce a proposal a reviewer can read in a second,
 * and the only way to say that honestly is to know which steps actually moved.
 *
 * <p>Matched **by step id**, not by position. Inserting a step at the top shifts every position
 * below it, and a positional diff would report the whole test as rewritten — which is exactly the
 * noise the doc warns about. Ids survive an insertion; positions do not.
 *
 * <p>In {@code core} rather than in a feature because two features need it: codegen describes the
 * impact of a regeneration, and the test case screen wants to say what a re-model would change
 * before anyone asks for one.
 */
public final class TestModelDiff {

  private TestModelDiff() {}

  /** How one step differs between the two versions. */
  public enum StepChange {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED
  }

  /**
   * @param stepId the IR step id, which is also what a run reports as the failing step — so an
   *     impact and a failure can be read against each other
   * @param page the page this step acts on, when it acts on one. Null for a step with no target.
   */
  public record StepDelta(String stepId, StepChange change, String page, String description) {}

  /**
   * @param steps every step that is not UNCHANGED, in the new model's order
   * @param pages the pages the changed steps touch — which page objects a reviewer should expect to
   *     matter, and which need inspecting if they never were
   * @param unchanged how many steps were left alone, so "3 of 14 changed" is sayable
   */
  public record Impact(List<StepDelta> steps, Set<String> pages, int unchanged) {

    public boolean isEmpty() {
      return steps.isEmpty();
    }

    /** True when the change is small enough that a whole-file rewrite would be noise. */
    public boolean isMinor() {
      return !steps.isEmpty() && steps.size() <= 3;
    }
  }

  /**
   * Compares two models by step id.
   *
   * <p>A null {@code previous} means nothing has been generated yet, so every step is an addition —
   * which reads correctly as "all of it is new" rather than as a diff against emptiness.
   */
  public static Impact between(TestModel previous, TestModel next) {
    Map<String, TestStep> before = byId(previous);
    Map<String, TestStep> after = byId(next);

    List<StepDelta> deltas = new ArrayList<>();
    Set<String> pages = new LinkedHashSet<>();
    int unchanged = 0;

    for (Map.Entry<String, TestStep> entry : after.entrySet()) {
      TestStep step = entry.getValue();
      TestStep old = before.get(entry.getKey());

      if (old == null) {
        deltas.add(delta(step, StepChange.ADDED));
        collectPage(step, pages);
      } else if (differs(old, step)) {
        deltas.add(delta(step, StepChange.MODIFIED));
        // Both sides: a step that moved from LoginPage to DashboardPage makes both matter — the
        // one it left may now have an element nothing references, and the one it arrived at may
        // never have been inspected.
        collectPage(old, pages);
        collectPage(step, pages);
      } else {
        unchanged++;
      }
    }

    for (Map.Entry<String, TestStep> entry : before.entrySet()) {
      if (!after.containsKey(entry.getKey())) {
        deltas.add(delta(entry.getValue(), StepChange.REMOVED));
        collectPage(entry.getValue(), pages);
      }
    }

    return new Impact(List.copyOf(deltas), Set.copyOf(pages), unchanged);
  }

  /**
   * Whether two steps with the same id say the same thing.
   *
   * <p>Compared field by field rather than with {@code equals} on the record, because {@code
   * derived} is a {@code Boolean} whose null and false mean the same thing — a record equality
   * would call those two steps different and report a change nobody made.
   */
  private static boolean differs(TestStep a, TestStep b) {
    return a.action() != b.action()
        || !Objects.equals(a.target(), b.target())
        || !Objects.equals(a.to(), b.to())
        || !Objects.equals(a.value(), b.value())
        || !Objects.equals(a.assertion(), b.assertion())
        || !Objects.equals(a.flow(), b.flow())
        || !Objects.equals(a.description(), b.description())
        || a.isDerived() != b.isDerived()
        || !Objects.equals(a.sourceStepIds(), b.sourceStepIds());
  }

  private static StepDelta delta(TestStep step, StepChange change) {
    String page = step.target() != null && step.target().isPage() ? step.target().page() : null;
    return new StepDelta(step.id(), change, page, describe(step));
  }

  /** Enough for a reviewer to recognise the step without opening the IR. */
  private static String describe(TestStep step) {
    if (step.description() != null && !step.description().isBlank()) {
      return step.description();
    }
    StringBuilder text = new StringBuilder(step.action().code());
    if (step.target() != null && step.target().isPage()) {
      text.append(' ').append(step.target().page());
      if (step.target().element() != null) {
        text.append('.').append(step.target().element());
      }
    }
    return text.toString();
  }

  private static void collectPage(TestStep step, Set<String> pages) {
    addPage(step.target(), pages);
    addPage(step.to(), pages);
  }

  private static void addPage(Target target, Set<String> pages) {
    if (target != null && target.isPage()) {
      pages.add(target.page());
    }
  }

  /** Insertion-ordered, so the deltas come back in the order the model reads. */
  private static Map<String, TestStep> byId(TestModel model) {
    Map<String, TestStep> steps = new LinkedHashMap<>();
    if (model != null) {
      model.allSteps().forEach(step -> steps.put(step.id(), step));
    }
    return steps;
  }
}
