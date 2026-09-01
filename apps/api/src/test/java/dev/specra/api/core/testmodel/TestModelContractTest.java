package dev.specra.api.core.testmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The two representations of the Test Model, checked against the same fixtures.
 *
 * <p>The schema in {@code packages/test-model} is the contract; these Java records are a typed
 * mirror of it. Nothing stops the two drifting except this test, so it is deliberately blunt: every
 * fixture the schema accepts must bind here, every fixture the schema rejects must be rejected, and
 * the closed vocabularies must match constant for constant.
 *
 * <p>No Spring context and no Docker — it runs in {@code ./mvnw test}.
 */
class TestModelContractTest {

  private final TestModelSchema schema = new TestModelSchema();

  private static Stream<Resource> valid() {
    return fixtures("valid");
  }

  private static Stream<Resource> schemaInvalid() {
    return fixtures("invalid/schema");
  }

  private static Stream<Resource> semanticInvalid() {
    return fixtures("invalid/semantic");
  }

  private static Stream<Resource> fixtures(String kind) {
    try {
      Resource[] found =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath:testmodel/fixtures/" + kind + "/*.json");
      assertThat(found)
          .describedAs(
              "fixtures for '%s' are copied from packages/test-model by the build; none were found,"
                  + " so the copy did not run",
              kind)
          .isNotEmpty();
      return Arrays.stream(found);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String read(Resource resource) throws IOException {
    return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  @ParameterizedTest(name = "accepts {0}")
  @MethodSource("valid")
  void everyValidFixtureIsAcceptedAndBinds(Resource fixture) throws IOException {
    JsonNode document = TestModelJson.tree(read(fixture));

    assertThat(schema.validate(document)).isEmpty();

    TestModel model = TestModelJson.read(document);
    assertThat(model.irVersion()).isEqualTo(TestModel.CURRENT_IR_VERSION);
    assertThat(model.name()).isNotBlank();
    assertThat(model.steps()).isNotEmpty();
  }

  @ParameterizedTest(name = "survives a round trip: {0}")
  @MethodSource("valid")
  void writingWhatWasReadIsStillAValidTestModel(Resource fixture) throws IOException {
    TestModel model = TestModelJson.read(read(fixture));

    JsonNode rewritten = TestModelJson.tree(TestModelJson.write(model));

    assertThat(schema.validate(rewritten)).isEmpty();
    assertThat(TestModelJson.read(rewritten)).isEqualTo(model);
  }

  @ParameterizedTest(name = "rejects {0}")
  @MethodSource("schemaInvalid")
  void everySchemaInvalidFixtureIsRejected(Resource fixture) throws IOException {
    assertThat(schema.validate(TestModelJson.tree(read(fixture))))
        .describedAs("the schema must reject %s", fixture.getFilename())
        .isNotEmpty();
  }

  @ParameterizedTest(name = "lets {0} through — the semantic layer's to reject")
  @MethodSource("semanticInvalid")
  void semanticFixturesAreStructurallyFine(Resource fixture) throws IOException {
    assertThat(schema.validate(TestModelJson.tree(read(fixture))))
        .describedAs(
            "%s is schema-valid on purpose: it documents where structural validation stops",
            fixture.getFilename())
        .isEmpty();
  }

  @Test
  @DisplayName("an unknown field is an error here too, not only in the schema")
  void bindingRejectsAnUnknownField() {
    String withAnEngine =
        """
        {
          "irVersion": 1,
          "name": "Open the home page",
          "engine": "playwright",
          "steps": [
            { "id": "s1", "sourceStepIds": ["ts-1"], "action": "navigate",
              "target": { "page": "HomePage" } }
          ]
        }
        """;

    assertThatThrownBy(() -> TestModelJson.read(withAnEngine)).hasMessageContaining("engine");
  }

  @Test
  @DisplayName("the violation names the step that broke the rule")
  void aViolationPointsAtItsStep() throws IOException {
    String fillWithoutAValue =
        """
        {
          "irVersion": 1,
          "name": "Type into the username field",
          "steps": [
            { "id": "s1", "sourceStepIds": ["ts-1"], "action": "fill",
              "target": { "page": "LoginPage", "element": "usernameInput" } }
          ]
        }
        """;

    List<SchemaViolation> violations = schema.validate(TestModelJson.tree(fillWithoutAValue));

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(violation -> violation.path().contains("steps[0]"));
  }
}
