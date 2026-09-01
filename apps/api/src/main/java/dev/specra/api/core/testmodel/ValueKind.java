package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Where a value comes from.
 *
 * <p>{@link #SECRET} is a name, never a value: the value is supplied by an Environment at dispatch
 * and must never reach this document, a prompt, a generated file or a log.
 */
public enum ValueKind {
  LITERAL("literal"),
  PARAM("param"),
  SECRET("secret");

  private final String code;

  ValueKind(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static ValueKind of(String code) {
    return Arrays.stream(values())
        .filter(kind -> kind.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown value kind: " + code));
  }
}
