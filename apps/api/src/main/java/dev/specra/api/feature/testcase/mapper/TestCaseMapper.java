package dev.specra.api.feature.testcase.mapper;

import dev.specra.api.feature.testcase.domain.TestCase;
import dev.specra.api.feature.testcase.domain.TestCaseStep;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testcase.dto.TestCaseSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Responses only. The way in is not a field copy — references are minted, steps renumbered, tags
 * normalised — so {@code TestCaseService} builds and mutates the entity itself.
 */
@Mapper(componentModel = "spring")
public interface TestCaseMapper {

  @Mapping(target = "action", source = "actionText")
  @Mapping(target = "data", source = "testData")
  @Mapping(target = "expected", source = "expectedText")
  TestCaseStepResponse toStepResponse(TestCaseStep step);

  TestCaseResponse toResponse(TestCase testCase);

  TestCaseSummaryResponse toSummary(TestCase testCase);
}
