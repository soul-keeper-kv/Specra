package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Schema, then binding, then semantics — the order 02-test-model-ir.md fixes, in one place.
 *
 * <p>It lives in {@code core} because two callers now need exactly the same gates and neither may
 * reach into the other: the modelling service validating what a model produced, and the store
 * accepting an IR a person edited by hand. A document that reached storage through a second,
 * slightly different chain is how "the IR is always valid" stops being true — so there is one
 * chain, and the human path is not the lenient one.
 *
 * <p>Binding sits between the two checks rather than beside them on purpose. The schema works on
 * the raw tree because that is where structural errors are legible, the semantic layer works on
 * bound records because that is where meaning is, and a document that passes the schema can still
 * fail to bind — a closed vocabulary the schema spells differently from the enum, for instance.
 * Calling that a violation rather than an exception is what keeps a bad document a 422.
 */
@Component
public class TestModelValidation {

  private final TestModelSchema schema;
  private final TestModelSemantics semantics;

  public TestModelValidation(TestModelSchema schema, TestModelSemantics semantics) {
    this.schema = schema;
    this.semantics = semantics;
  }

  /** The full chain over a raw document. */
  public Result validate(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Result.invalid(List.of(new SchemaViolation("/", "no Test Model was supplied")));
    }

    List<SchemaViolation> structural = schema.validate(node);
    if (!structural.isEmpty()) {
      return Result.invalid(structural);
    }

    TestModel model;
    try {
      model = TestModelJson.read(node);
    } catch (JsonProcessingException e) {
      return Result.invalid(
          List.of(new SchemaViolation("/", "does not bind: " + e.getOriginalMessage())));
    }

    List<SchemaViolation> semantic = semantics.validate(model);
    return semantic.isEmpty() ? Result.ok(model) : Result.invalid(semantic);
  }

  /**
   * A valid model, or the reasons it is not one — never both, and never an exception. Whether a
   * refusal is a 422 or a repair round is the caller's decision, not this class's.
   */
  public record Result(TestModel model, List<SchemaViolation> violations) {

    public static Result ok(TestModel model) {
      return new Result(model, List.of());
    }

    public static Result invalid(List<SchemaViolation> violations) {
      return new Result(null, List.copyOf(violations));
    }

    public boolean ok() {
      return violations.isEmpty();
    }
  }
}
