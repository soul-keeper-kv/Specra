package dev.specra.api.core.content;

import java.util.Locale;
import org.springframework.lang.Nullable;

/**
 * A search request, already made safe.
 *
 * <p>Every field is normalised in the compact constructor rather than trusted, because the usual
 * caller is a language model: it will ask for page {@code -1}, for ten thousand results, or pass
 * {@code " "} as a tag. Clamping here means no store has to repeat the same defensive code, and no
 * prompt injection can turn a search into a full table scan.
 *
 * @param text matched against title and body, case-insensitive; {@code null} means "no filter"
 * @param tag exact tag match, lower-cased; {@code null} means "no filter"
 */
public record ContentQuery(@Nullable String text, @Nullable String tag, int page, int size) {

  public static final int DEFAULT_SIZE = 10;
  public static final int MAX_SIZE = 50;

  public ContentQuery {
    text = blankToNull(text);
    tag = tag == null ? null : blankToNull(tag.toLowerCase(Locale.ROOT));
    page = Math.max(page, 0);
    size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
  }

  /** First page, default size — the shape a tool call almost always wants. */
  public static ContentQuery of(
      @Nullable String text, @Nullable String tag, @Nullable Integer size) {
    return new ContentQuery(text, tag, 0, size == null ? DEFAULT_SIZE : size);
  }

  @Nullable private static String blankToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
