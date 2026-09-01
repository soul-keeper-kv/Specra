package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * The strategies the escape hatch allows.
 *
 * <p>Deliberately short: the ranked strategy list ({@code testId}, {@code role}, {@code label},
 * {@code text}, …) belongs to the locator planner and lives on the page object, not in an IR step.
 */
public enum SelectorStrategy {
  CSS("css"),
  XPATH("xpath");

  private final String code;

  SelectorStrategy(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static SelectorStrategy of(String code) {
    return Arrays.stream(values())
        .filter(strategy -> strategy.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown selector strategy: " + code));
  }
}
