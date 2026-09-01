package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One step of intent.
 *
 * <p>{@code sourceStepIds} is the traceability link back to the manual test case, and it is not
 * decoration: a step that names no source and is not marked {@code derived} is a step nobody asked
 * for, and the schema rejects it. That is the cheapest available guard against a hallucinated step.
 *
 * <p>Which of the optional fields a step must carry depends on its {@link StepAction}, and the
 * schema — not this record — is what enforces that.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestStep(
    String id,
    List<String> sourceStepIds,
    Boolean derived,
    String description,
    StepAction action,
    Target target,
    Target to,
    TestValue value,
    Assertion assertion,
    String flow) {

  public TestStep {
    sourceStepIds = sourceStepIds == null ? List.of() : List.copyOf(sourceStepIds);
  }

  @JsonIgnore
  public boolean isDerived() {
    return Boolean.TRUE.equals(derived);
  }
}
