package dev.specra.api.feature.testmodel.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.testmodel.SchemaViolation;
import java.util.List;
import java.util.Map;

/**
 * The model's document failed validation after its one repair round. Nothing was stored; the
 * violations ride along so the UI can show what the schema or the semantic layer objected to.
 */
public class TestModelInvalidException extends BusinessException {

  private final transient List<SchemaViolation> violations;

  public TestModelInvalidException(List<SchemaViolation> violations) {
    super(
        ErrorCode.TEST_MODEL_INVALID, ErrorCode.TEST_MODEL_INVALID.detailKey(), violations.size());
    this.violations = List.copyOf(violations);
  }

  public List<SchemaViolation> violations() {
    return violations;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of("violations", violations);
  }
}
