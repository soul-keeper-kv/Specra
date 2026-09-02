package dev.specra.api.core.testmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The semantic layer against the fixtures that were written for it: {@code invalid/semantic/} is
 * schema-valid on purpose, so each file there must be caught here — at the place it says — and
 * nothing in {@code valid/} may be.
 */
class TestModelSemanticsTest {

  /**
   * Where each semantic fixture goes wrong, so the test pins the pointer and not just "invalid".
   */
  private static final Map<String, String> EXPECTED_PATH =
      Map.of(
          "duplicate-step-ids.json", "/steps/1/id",
          "engine-vocabulary-in-a-description.json", "/steps/0/description",
          "no-assertion-anywhere.json", "/steps",
          "plain-value-on-a-secret-parameter.json", "/steps/0/value/kind",
          "secret-value-on-a-plain-parameter.json", "/steps/0/value/kind",
          "undeclared-parameter.json", "/steps/0/value/name");

  private final TestModelSemantics semantics = new TestModelSemantics();

  private static Stream<Resource> valid() {
    return fixtures("valid");
  }

  private static Stream<Resource> semanticInvalid() {
    return fixtures("invalid/semantic");
  }

  private static Stream<Resource> fixtures(String kind) {
    try {
      Resource[] found =
          new PathMatchingResourcePatternResolver()
              .getResources("classpath:testmodel/fixtures/" + kind + "/*.json");
      assertThat(found).describedAs("fixtures for '%s'", kind).isNotEmpty();
      return Stream.of(found);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static TestModel read(Resource resource) throws IOException {
    return TestModelJson.read(
        new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  @ParameterizedTest(name = "passes {0}")
  @MethodSource("valid")
  void everyValidFixturePasses(Resource fixture) throws IOException {
    assertThat(semantics.validate(read(fixture))).isEmpty();
  }

  @ParameterizedTest(name = "catches {0}")
  @MethodSource("semanticInvalid")
  void everySemanticFixtureIsCaughtWhereItSaysItIs(Resource fixture) throws IOException {
    String expectedPath = EXPECTED_PATH.get(fixture.getFilename());
    assertThat(expectedPath)
        .describedAs("%s is a new semantic fixture: say which rule it exercises", fixture)
        .isNotNull();
    assertThat(semantics.validate(read(fixture)))
        .extracting(SchemaViolation::path)
        .contains(expectedPath);
  }

  @ParameterizedTest(name = "{0} is a fixture this test knows about")
  @MethodSource("semanticInvalid")
  void everyKnownRuleStillHasItsFixture(Resource fixture) {
    assertThat(EXPECTED_PATH).containsKey(fixture.getFilename());
  }
}
