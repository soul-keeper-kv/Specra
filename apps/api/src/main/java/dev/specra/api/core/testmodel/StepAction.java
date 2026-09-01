package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Everything a test can do, and nothing else.
 *
 * <p>The vocabulary is closed on purpose. An action it cannot express means the manual step was
 * ambiguous, and the pipeline says so rather than inventing one. Notice what is missing: there is
 * no {@code sleep}, because a duration in an IR is a flake with a schema — waiting is a state.
 *
 * <p>The wire form is the schema's, so the constants carry their own code rather than relying on
 * {@code name()}.
 */
public enum StepAction {
  NAVIGATE("navigate"),
  RELOAD("reload"),
  GO_BACK("goBack"),
  GO_FORWARD("goForward"),
  FILL("fill"),
  CLEAR("clear"),
  PRESS("press"),
  SELECT("select"),
  CHECK("check"),
  UNCHECK("uncheck"),
  UPLOAD("upload"),
  CLICK("click"),
  DOUBLE_CLICK("doubleClick"),
  RIGHT_CLICK("rightClick"),
  HOVER("hover"),
  DRAG_TO("dragTo"),
  WAIT_FOR("waitFor"),
  ASSERT("assert"),
  USE_FLOW("useFlow");

  private final String code;

  StepAction(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static StepAction of(String code) {
    return Arrays.stream(values())
        .filter(action -> action.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown action: " + code));
  }
}
