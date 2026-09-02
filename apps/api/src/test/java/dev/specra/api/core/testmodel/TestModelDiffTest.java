package dev.specra.api.core.testmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * "Regeneration is a diff, not a rewrite" (08-ai-pipeline.md), as a test.
 *
 * <p>The claim worth defending is the one about noise: a one-step change must read as a one-step
 * change. Everything here is a way of checking that the diff does not manufacture churn.
 */
class TestModelDiffTest {

  @Test
  void aRenamedElementIsOneModifiedStep() {
    TestModel before =
        model(step("s1", "LoginPage", "loginButton"), step("s2", "LoginPage", "email"));
    TestModel after =
        model(step("s1", "LoginPage", "signInButton"), step("s2", "LoginPage", "email"));

    TestModelDiff.Impact impact = TestModelDiff.between(before, after);

    assertThat(impact.steps())
        .singleElement()
        .satisfies(
            delta -> {
              assertThat(delta.stepId()).isEqualTo("s1");
              assertThat(delta.change()).isEqualTo(TestModelDiff.StepChange.MODIFIED);
              assertThat(delta.page()).isEqualTo("LoginPage");
            });
    assertThat(impact.unchanged()).isEqualTo(1);
    // Small enough that a whole-file rewrite would be noise — which is the point of the flag.
    assertThat(impact.isMinor()).isTrue();
  }

  /**
   * The reason this matches by id rather than by position. Inserting a step at the top shifts every
   * position below it, and a positional diff would report the entire test as rewritten — exactly
   * the churn the doc warns about.
   */
  @Test
  void insertingAStepAtTheTopDoesNotReportTheRestAsChanged() {
    TestModel before = model(step("s1", "LoginPage", "email"), step("s2", "LoginPage", "submit"));
    TestModel after =
        model(
            step("s0", "LoginPage", "banner"),
            step("s1", "LoginPage", "email"),
            step("s2", "LoginPage", "submit"));

    TestModelDiff.Impact impact = TestModelDiff.between(before, after);

    assertThat(impact.steps())
        .singleElement()
        .satisfies(
            delta -> {
              assertThat(delta.stepId()).isEqualTo("s0");
              assertThat(delta.change()).isEqualTo(TestModelDiff.StepChange.ADDED);
            });
    assertThat(impact.unchanged()).isEqualTo(2);
  }

  @Test
  void aDeletedStepIsReportedAsRemoved() {
    TestModel before = model(step("s1", "LoginPage", "email"), step("s2", "LoginPage", "submit"));
    TestModel after = model(step("s1", "LoginPage", "email"));

    TestModelDiff.Impact impact = TestModelDiff.between(before, after);

    assertThat(impact.steps())
        .singleElement()
        .satisfies(
            delta -> {
              assertThat(delta.stepId()).isEqualTo("s2");
              assertThat(delta.change()).isEqualTo(TestModelDiff.StepChange.REMOVED);
            });
  }

  @Test
  void anIdenticalModelHasNoImpact() {
    TestModel model = model(step("s1", "LoginPage", "email"));

    assertThat(TestModelDiff.between(model, model).isEmpty()).isTrue();
  }

  /** A first generation has no baseline, and every step reads correctly as new. */
  @Test
  void aNullBaselineMakesEveryStepAnAddition() {
    TestModel after = model(step("s1", "LoginPage", "email"), step("s2", "LoginPage", "submit"));

    TestModelDiff.Impact impact = TestModelDiff.between(null, after);

    assertThat(impact.steps()).hasSize(2);
    assertThat(impact.steps())
        .allSatisfy(delta -> assertThat(delta.change()).isEqualTo(TestModelDiff.StepChange.ADDED));
    assertThat(impact.unchanged()).isZero();
  }

  /**
   * `derived` is a Boolean whose null and false mean the same thing. Comparing records with
   * `equals` would call these two steps different and report a change nobody made.
   */
  @Test
  void aNullAndAFalseDerivedFlagAreTheSameStep() {
    TestStep withNull =
        new TestStep(
            "s1",
            List.of("ts-1"),
            null,
            null,
            StepAction.CLICK,
            target("P", "e"),
            null,
            null,
            null,
            null);
    TestStep withFalse =
        new TestStep(
            "s1",
            List.of("ts-1"),
            false,
            null,
            StepAction.CLICK,
            target("P", "e"),
            null,
            null,
            null,
            null);

    assertThat(TestModelDiff.between(model(withNull), model(withFalse)).isEmpty()).isTrue();
  }

  /**
   * A step that moved between pages makes both matter: the one it left may now have an element
   * nothing references, and the one it arrived at may never have been inspected.
   */
  @Test
  void aStepThatMovedBetweenPagesReportsBoth() {
    TestModel before = model(step("s1", "LoginPage", "next"));
    TestModel after = model(step("s1", "DashboardPage", "next"));

    assertThat(TestModelDiff.between(before, after).pages())
        .containsExactlyInAnyOrder("LoginPage", "DashboardPage");
  }

  /** Beyond a handful of steps, a large file diff is the honest rendering of a large change. */
  @Test
  void aLargeChangeIsNotMinor() {
    TestModel before = model(step("s1", "P", "a"));
    TestModel after =
        model(
            step("s1", "P", "z"), step("s2", "P", "b"), step("s3", "P", "c"), step("s4", "P", "d"));

    TestModelDiff.Impact impact = TestModelDiff.between(before, after);

    assertThat(impact.steps()).hasSize(4);
    assertThat(impact.isMinor()).isFalse();
  }

  // ── fixtures ────────────────────────────────────────────────────────────────

  private static TestModel model(TestStep... steps) {
    return new TestModel(1, "A test", null, null, null, null, List.of(steps), null);
  }

  private static TestStep step(String id, String page, String element) {
    return new TestStep(
        id,
        List.of("ts-1"),
        null,
        null,
        StepAction.CLICK,
        target(page, element),
        null,
        null,
        null,
        null);
  }

  private static Target target(String page, String element) {
    return new Target(page, element, null);
  }
}
