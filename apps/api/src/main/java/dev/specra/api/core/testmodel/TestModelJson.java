package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Reading and writing a Test Model, with its own mapper.
 *
 * <p>Its own, and not the application's, on purpose: the contract is that an unknown field is an
 * error, and Spring Boot turns {@code FAIL_ON_UNKNOWN_PROPERTIES} off by default. A document that
 * the schema rejects for carrying {@code engine: "playwright"} must not quietly bind here.
 */
public final class TestModelJson {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private TestModelJson() {}

  public static TestModel read(String json) throws JsonProcessingException {
    return MAPPER.readValue(json, TestModel.class);
  }

  public static TestModel read(JsonNode node) throws JsonProcessingException {
    return MAPPER.treeToValue(node, TestModel.class);
  }

  public static JsonNode tree(String json) throws JsonProcessingException {
    return MAPPER.readTree(json);
  }

  public static String write(TestModel model) throws JsonProcessingException {
    return MAPPER.writeValueAsString(model);
  }
}
