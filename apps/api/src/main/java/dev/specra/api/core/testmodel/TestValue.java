package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A value a step carries.
 *
 * <p>A {@link ValueKind#LITERAL} holds its own {@code value}; {@link ValueKind#PARAM} and {@link
 * ValueKind#SECRET} hold only a {@code name} that an Environment resolves at run time. A secret's
 * value is never here.
 *
 * <p>{@code value} is {@code Object} because the schema allows a string, a number or a boolean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestValue(ValueKind kind, Object value, String name) {

  @JsonIgnore
  public boolean isSecret() {
    return kind == ValueKind.SECRET;
  }
}
