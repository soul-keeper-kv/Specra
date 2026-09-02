package dev.specra.api.core.testmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The semantic layer: what a document can get past the schema and still be wrong about.
 *
 * <p>Runs where an IR is authored and stored — here — and nowhere else, so the runner never sees a
 * document that failed it. {@code packages/test-model/fixtures/invalid/semantic/} holds one
 * schema-valid document per rule below; {@code TestModelSemanticsTest} checks each is caught.
 *
 * <p>Same shape of result as the schema layer so a client renders both the same way: a JSON Pointer
 * to the offending place and a sentence about it.
 */
@Component
public class TestModelSemantics {

  /**
   * Engine vocabulary that has no business in free text. Loose on purpose: a description that reads
   * like a line of automation code is a model that skipped the modelling step, whichever engine the
   * line was for.
   */
  static final Pattern ENGINE_VOCABULARY =
      Pattern.compile(
          "\\bgetBy[A-Z]\\w*|\\blocator\\b|\\bexpect\\s*\\(|\\bcy\\.\\w|\\bdriver\\b|\\bawait\\b"
              + "|\\bpage\\.\\w+\\(|\\bquerySelector|\\bxpath\\s*=|\\bselenium\\b|\\bplaywright\\b",
          Pattern.CASE_INSENSITIVE);

  /** Empty means valid. */
  public List<SchemaViolation> validate(TestModel model) {
    List<SchemaViolation> violations = new ArrayList<>();
    checkEngineVocabulary("/name", model.name(), violations);
    checkEngineVocabulary("/description", model.description(), violations);

    Map<String, TestParameter> parameters = new HashMap<>();
    for (TestParameter parameter : model.parameters()) {
      parameters.put(parameter.name(), parameter);
    }

    Set<String> ids = new HashSet<>();
    boolean asserts = false;
    for (Section section : Section.values()) {
      List<TestStep> steps = section.of(model);
      for (int i = 0; i < steps.size(); i++) {
        TestStep step = steps.get(i);
        String path = "/" + section.key + "/" + i;
        if (!ids.add(step.id())) {
          violations.add(
              new SchemaViolation(path + "/id", "duplicate step id '" + step.id() + "'"));
        }
        checkEngineVocabulary(path + "/description", step.description(), violations);
        checkValue(path, step.value(), parameters, violations);
        if (step.action() == StepAction.ASSERT) {
          asserts = true;
        }
      }
    }
    if (!asserts) {
      violations.add(
          new SchemaViolation("/steps", "the test asserts nothing, so it can only ever pass"));
    }
    return List.copyOf(violations);
  }

  private static void checkValue(
      String path,
      TestValue value,
      Map<String, TestParameter> parameters,
      List<SchemaViolation> violations) {
    if (value == null || value.kind() == ValueKind.LITERAL) {
      return;
    }
    TestParameter parameter = parameters.get(value.name());
    if (parameter == null) {
      violations.add(
          new SchemaViolation(
              path + "/value/name", "parameter '" + value.name() + "' is not declared"));
      return;
    }
    if (value.isSecret() && !parameter.isSecret()) {
      violations.add(
          new SchemaViolation(
              path + "/value/kind",
              "'" + value.name() + "' is read as a secret but is declared as a plain parameter"));
    } else if (!value.isSecret() && parameter.isSecret()) {
      violations.add(
          new SchemaViolation(
              path + "/value/kind",
              "'"
                  + value.name()
                  + "' is a secret and must be read as one, never as a plain value"));
    }
  }

  private static void checkEngineVocabulary(
      String path, String text, List<SchemaViolation> violations) {
    if (text != null && ENGINE_VOCABULARY.matcher(text).find()) {
      violations.add(
          new SchemaViolation(
              path, "reads like automation code; the model describes intent, not an engine"));
    }
  }

  private enum Section {
    SETUP("setup"),
    STEPS("steps"),
    TEARDOWN("teardown");

    private final String key;

    Section(String key) {
      this.key = key;
    }

    List<TestStep> of(TestModel model) {
      return switch (this) {
        case SETUP -> model.setup();
        case STEPS -> model.steps();
        case TEARDOWN -> model.teardown();
      };
    }
  }
}
