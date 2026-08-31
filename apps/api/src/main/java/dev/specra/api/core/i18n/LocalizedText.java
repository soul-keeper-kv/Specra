package dev.specra.api.core.i18n;

/**
 * A message argument that is itself a translation key.
 *
 * <p>Without this, {@code "Note 7f3c… not found"} would keep the English word "Note" inside an
 * otherwise Vietnamese sentence, because message arguments are substituted verbatim. {@link
 * MessageResolver} unwraps these before formatting, so the whole sentence ends up in one language.
 *
 * @param key a key in the {@code i18n/messages} bundle, e.g. {@code resource.note}
 */
public record LocalizedText(String key) {}
