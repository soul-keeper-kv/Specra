package dev.specra.api.core.testmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The Java enums against the schema's enums, constant for constant.
 *
 * <p>The action list is closed on purpose, so the failure mode worth guarding is a new action added
 * to the schema and forgotten here — or the reverse. `packages/test-model` has the mirror of this
 * test for its TypeScript types.
 */
class TestModelVocabularyTest {

  private final JsonNode schema = load();

  private static JsonNode load() {
    try (InputStream stream =
        new ClassPathResource(TestModelSchema.SCHEMA_RESOURCE).getInputStream()) {
      return TestModelJson.tree(
          new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<String> schemaEnum(String... path) {
    JsonNode node = schema;
    for (String key : path) {
      node = node.get(key);
      assertThat(node).describedAs("no node at %s", String.join("/", path)).isNotNull();
    }
    return StreamSupport.stream(node.spliterator(), false).map(JsonNode::asText).sorted().toList();
  }

  @Test
  void actionsMatch() {
    assertThat(Arrays.stream(StepAction.values()).map(StepAction::code).sorted().toList())
        .isEqualTo(schemaEnum("$defs", "step", "properties", "action", "enum"));
  }

  @Test
  void assertionConditionsMatch() {
    assertThat(Arrays.stream(AssertCondition.values()).map(AssertCondition::code).sorted().toList())
        .isEqualTo(schemaEnum("$defs", "assertion", "properties", "condition", "enum"));
  }

  @Test
  void parameterTypesMatch() {
    assertThat(Arrays.stream(ParameterType.values()).map(ParameterType::code).sorted().toList())
        .isEqualTo(schemaEnum("$defs", "parameter", "properties", "type", "enum"));
  }

  @Test
  void selectorStrategiesMatch() {
    assertThat(
            Arrays.stream(SelectorStrategy.values()).map(SelectorStrategy::code).sorted().toList())
        .isEqualTo(
            schemaEnum(
                "$defs",
                "selectorTarget",
                "properties",
                "selector",
                "properties",
                "strategy",
                "enum"));
  }

  @Test
  void valueKindsMatch() {
    JsonNode value = schema.get("$defs").get("value");
    String literal = value.get("oneOf").get(0).get("properties").get("kind").get("const").asText();
    List<String> named =
        StreamSupport.stream(
                value.get("oneOf").get(1).get("properties").get("kind").get("enum").spliterator(),
                false)
            .map(JsonNode::asText)
            .toList();

    assertThat(Arrays.stream(ValueKind.values()).map(ValueKind::code).sorted().toList())
        .isEqualTo(
            java.util.stream.Stream.concat(java.util.stream.Stream.of(literal), named.stream())
                .sorted()
                .toList());
  }

  @Test
  void theSchemaIsTheVersionTheRecordClaims() {
    assertThat(schema.get("properties").get("irVersion").get("const").asInt())
        .isEqualTo(TestModel.CURRENT_IR_VERSION);
    assertThat(schema.get("$id").asText()).endsWith("test-model.v1.schema.json");
  }

  @Test
  void thereIsNoWayToExpressADuration() {
    JsonNode stepProperties = schema.get("$defs").get("step").get("properties");

    assertThat(schema.get("$defs").get("step").get("additionalProperties").asBoolean()).isFalse();
    assertThat(stepProperties.fieldNames())
        .toIterable()
        .noneMatch(
            field ->
                field
                    .toLowerCase(java.util.Locale.ROOT)
                    .matches(".*(timeout|delay|sleep|wait_?ms).*"));
    assertThat(Arrays.stream(StepAction.values()).map(StepAction::code).toList())
        .doesNotContain("sleep", "wait", "pause");
  }
}
