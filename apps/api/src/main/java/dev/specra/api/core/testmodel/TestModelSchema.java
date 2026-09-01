package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Structural validation of a Test Model against the shared v1 schema.
 *
 * <p>Shared literally: {@code test-model.v1.schema.json} lives in {@code packages/test-model} and
 * is copied onto this module's classpath by the build, so the web app, the runner and this service
 * validate against the same bytes. Mirroring the rules in Java instead would be two implementations
 * of one contract, and they would drift.
 *
 * <p>This is only the first of three layers. Referential checks (does that page object exist) and
 * semantic ones (does the test assert anything at all) run above it, where the IR is authored.
 */
@Component
public class TestModelSchema {

  static final String SCHEMA_RESOURCE = "testmodel/test-model.v1.schema.json";

  private final JsonSchema schema;

  public TestModelSchema() {
    this.schema = load();
  }

  private static JsonSchema load() {
    try (InputStream stream = new ClassPathResource(SCHEMA_RESOURCE).getInputStream()) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(stream);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "The Test Model schema is missing from the classpath. It is copied from"
              + " packages/test-model by the build, so a stale target/ is the usual cause.",
          e);
    }
  }

  /**
   * Empty means valid. Violations are ordered by where they are, so the first one is the top one.
   */
  public List<SchemaViolation> validate(JsonNode document) {
    return schema.validate(document).stream()
        .map(
            message ->
                new SchemaViolation(message.getInstanceLocation().toString(), message.getMessage()))
        .sorted(Comparator.comparing(SchemaViolation::path))
        .toList();
  }

  public boolean isValid(JsonNode document) {
    return validate(document).isEmpty();
  }
}
