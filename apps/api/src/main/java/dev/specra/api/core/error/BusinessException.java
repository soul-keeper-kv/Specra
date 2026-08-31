package dev.specra.api.core.error;

import java.util.Arrays;
import org.springframework.lang.Nullable;

/**
 * Base class for failures that are part of the domain rather than a bug.
 *
 * <p>These carry a translation key instead of a message: the text is produced by {@link
 * ProblemFactory} in the caller's locale at the edge, so a service never has to know which language
 * it is running for. {@link RuntimeException#getMessage()} still returns something readable, but it
 * is for the log, not for the client.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String messageKey;
  private final transient Object[] messageArgs;

  public BusinessException(
      ErrorCode errorCode, String messageKey, @Nullable Object... messageArgs) {
    super(describe(messageKey, messageArgs));
    this.errorCode = errorCode;
    this.messageKey = messageKey;
    this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
  }

  /**
   * What lands in the log and in a stack trace. The key alone would not say <em>which</em> note was
   * missing, so the arguments are appended untranslated — this text is for whoever is debugging,
   * and the client never sees it.
   */
  private static String describe(String messageKey, @Nullable Object[] messageArgs) {
    return messageArgs == null || messageArgs.length == 0
        ? messageKey
        : messageKey + " " + Arrays.toString(messageArgs);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public String messageKey() {
    return messageKey;
  }

  public Object[] messageArgs() {
    return messageArgs.clone();
  }
}
