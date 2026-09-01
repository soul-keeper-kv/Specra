package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** The types a declared parameter may take. */
public enum ParameterType {
  STRING("string"),
  NUMBER("number"),
  BOOLEAN("boolean");

  private final String code;

  ParameterType(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static ParameterType of(String code) {
    return Arrays.stream(values())
        .filter(type -> type.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown parameter type: " + code));
  }
}
