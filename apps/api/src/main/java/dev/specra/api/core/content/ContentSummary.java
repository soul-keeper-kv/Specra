package dev.specra.api.core.content;

import java.time.Instant;
import java.util.Set;

/**
 * A search hit: enough to decide whether to open the document, not the document itself.
 *
 * <p>The split from {@link ContentDocument} is not cosmetic. Search results are fed straight back
 * to a language model, and a page of full bodies would consume the context window before the model
 * has read any of it. The model gets excerpts and ids, then fetches only what it needs.
 */
public record ContentSummary(
    String kind, String id, String title, String excerpt, Set<String> tags, Instant updatedAt) {

  /** Long enough to recognise a document by, short enough that twenty of them stay cheap. */
  public static final int EXCERPT_CHARS = 200;

  /**
   * Collapse every run of whitespace to one space, then cut.
   *
   * <p>Flattening first is what makes the length predictable: a body that is mostly blank lines
   * would otherwise spend its whole excerpt on them and show the model nothing.
   */
  public static String excerpt(String body) {
    if (body == null) {
      return "";
    }
    String flattened = flatten(body);
    return flattened.length() <= EXCERPT_CHARS
        ? flattened
        : flattened.substring(0, EXCERPT_CHARS) + "…";
  }

  private static String flatten(String body) {
    StringBuilder out = new StringBuilder(body.length());
    boolean gap = false;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (Character.isWhitespace(c)) {
        gap = !out.isEmpty();
      } else {
        if (gap) {
          out.append(' ');
          gap = false;
        }
        out.append(c);
      }
    }
    return out.toString();
  }
}
