package dev.specra.api.core.i18n;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * The one way this codebase turns a message key into text.
 *
 * <p>Everything user-facing — error titles, error details, validation failures — goes through here
 * so no English string is ever hard-coded into a response. The locale comes from {@link
 * LocaleContextHolder}, which Spring MVC fills in per request from {@code Accept-Language}.
 */
@Component
public class MessageResolver {

  private final MessageSource messageSource;

  public MessageResolver(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /** Resolves {@code key} in the caller's locale; returns the key itself if it is missing. */
  public String get(String key, @Nullable Object... args) {
    return get(LocaleContextHolder.getLocale(), key, args);
  }

  public String get(Locale locale, String key, @Nullable Object... args) {
    return messageSource.getMessage(key, resolveArgs(locale, args), key, locale);
  }

  /**
   * Resolves {@code key}, falling back to {@code fallback} rather than to the key. Used where the
   * upstream text (a Bean Validation message, a provider's error) is already a usable sentence.
   */
  public String getOrDefault(String key, String fallback, @Nullable Object... args) {
    Locale locale = LocaleContextHolder.getLocale();
    return messageSource.getMessage(key, resolveArgs(locale, args), fallback, locale);
  }

  /** Nested {@link LocalizedText} arguments are translated before they are substituted. */
  private Object[] resolveArgs(Locale locale, @Nullable Object[] args) {
    if (args == null || args.length == 0) {
      return new Object[0];
    }
    Object[] resolved = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      resolved[i] =
          arg instanceof LocalizedText text
              ? messageSource.getMessage(text.key(), null, text.key(), locale)
              : arg;
    }
    return resolved;
  }
}
