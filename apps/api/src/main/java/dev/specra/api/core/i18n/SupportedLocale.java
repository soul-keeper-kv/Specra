package dev.specra.api.core.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The locales this API actually has translations for.
 *
 * <p>Kept as an enum rather than a property list so a typo is a compile error, and so {@code
 * AcceptHeaderLocaleResolver} and the OpenAPI documentation can be fed from one place. Adding a
 * language means adding a constant here and a {@code messages_xx.properties} beside the default
 * bundle — nothing else.
 */
public enum SupportedLocale {
  EN(Locale.ENGLISH),
  VI(Locale.forLanguageTag("vi"));

  /** What an unknown or missing {@code Accept-Language} falls back to. */
  public static final SupportedLocale DEFAULT = EN;

  private final Locale locale;

  SupportedLocale(Locale locale) {
    this.locale = locale;
  }

  public Locale locale() {
    return locale;
  }

  public String tag() {
    return locale.toLanguageTag();
  }

  public static List<Locale> locales() {
    return Arrays.stream(values()).map(SupportedLocale::locale).toList();
  }

  public static List<String> tags() {
    return Arrays.stream(values()).map(SupportedLocale::tag).toList();
  }

  /** Matches on language only, so {@code vi-VN} and {@code vi} both resolve to Vietnamese. */
  public static SupportedLocale from(Locale locale) {
    if (locale == null) {
      return DEFAULT;
    }
    return Arrays.stream(values())
        .filter(candidate -> candidate.locale.getLanguage().equals(locale.getLanguage()))
        .findFirst()
        .orElse(DEFAULT);
  }
}
