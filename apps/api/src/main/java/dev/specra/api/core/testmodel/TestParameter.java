package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An input the test declares and an Environment supplies.
 *
 * <p>{@code secret} is what keeps a credential out of the repository: a secret parameter is read
 * from the process environment by the generated code, masked in captured output, and never written
 * into a spec, a fixture or a commit.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestParameter(
    String name, ParameterType type, Boolean required, Boolean secret, String description) {

  @JsonIgnore
  public boolean isSecret() {
    return Boolean.TRUE.equals(secret);
  }

  @JsonIgnore
  public boolean isRequired() {
    return required == null || required;
  }
}
