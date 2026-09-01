package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** What an {@code assert} states now, and what a {@code waitFor} waits for. */
public enum AssertCondition {
  VISIBLE("visible"),
  HIDDEN("hidden"),
  ENABLED("enabled"),
  DISABLED("disabled"),
  CHECKED("checked"),
  TEXT_EQUALS("textEquals"),
  TEXT_CONTAINS("textContains"),
  VALUE_EQUALS("valueEquals"),
  COUNT_EQUALS("countEquals"),
  URL_MATCHES("urlMatches"),
  ATTRIBUTE_EQUALS("attributeEquals");

  private final String code;

  AssertCondition(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static AssertCondition of(String code) {
    return Arrays.stream(values())
        .filter(condition -> condition.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown condition: " + code));
  }
}
