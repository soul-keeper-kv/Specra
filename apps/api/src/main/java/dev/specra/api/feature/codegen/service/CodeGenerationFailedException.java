package dev.specra.api.feature.codegen.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.util.Map;

/**
 * The runner refused the job.
 *
 * <p>A refusal, not a fault: an IR that fails the schema, a page object whose element collides, two
 * pages of the same name. The runner's own message travels with the problem because it names the
 * thing to fix, and translating it into a generic sentence would throw that away.
 */
public class CodeGenerationFailedException extends BusinessException {

  private final transient String runnerCode;
  private final transient String runnerMessage;

  public CodeGenerationFailedException(String runnerCode, String runnerMessage) {
    super(ErrorCode.TEST_MODEL_INVALID, "error.code-generation.refused", runnerMessage);
    this.runnerCode = runnerCode;
    this.runnerMessage = runnerMessage;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of("runnerCode", runnerCode, "runnerMessage", runnerMessage);
  }
}
