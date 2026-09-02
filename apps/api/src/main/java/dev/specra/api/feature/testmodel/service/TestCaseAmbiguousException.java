package dev.specra.api.feature.testmodel.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.feature.testmodel.dto.AmbiguityQuestion;
import java.util.List;
import java.util.Map;

/**
 * Understanding stopped rather than guessed. The questions ride along as a problem extension so the
 * workspace can put each one next to the manual step it is about.
 */
public class TestCaseAmbiguousException extends BusinessException {

  private final transient List<AmbiguityQuestion> questions;

  public TestCaseAmbiguousException(List<AmbiguityQuestion> questions) {
    this(ErrorCode.TEST_CASE_AMBIGUOUS.detailKey(), questions);
  }

  public TestCaseAmbiguousException(String messageKey, List<AmbiguityQuestion> questions) {
    super(ErrorCode.TEST_CASE_AMBIGUOUS, messageKey, questions.size());
    this.questions = List.copyOf(questions);
  }

  public List<AmbiguityQuestion> questions() {
    return questions;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of("questions", questions);
  }
}
