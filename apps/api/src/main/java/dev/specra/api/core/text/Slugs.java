package dev.specra.api.core.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a name a human typed into the URL-safe handle stored beside it.
 *
 * <p>Vietnamese is the default language, so the first step is not optional: {@code "Kiểm thử"}
 * decomposes to {@code "Kie<combining hook>m thu<combining horn>"}, and only then does dropping
 * everything outside {@code [a-z0-9-]} leave {@code "kiem-thu"} rather than {@code "kim-th"}. The
 * đ/Đ pair has no decomposed form and is replaced by hand.
 */
public final class Slugs {

  private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
  private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGES = Pattern.compile("^-+|-+$");

  private Slugs() {}

  /** Empty when the input contains nothing that survives — the caller decides what that means. */
  public static String of(String text, int maxLength) {
    if (text == null) {
      return "";
    }
    String folded = text.replace('đ', 'd').replace('Đ', 'D');
    String ascii =
        COMBINING.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
    String hyphenated = SEPARATORS.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
    String slug = EDGES.matcher(hyphenated).replaceAll("");
    return slug.length() <= maxLength ? slug : truncate(slug, maxLength);
  }

  /**
   * Cuts back to a word boundary rather than mid-word, so a long name shortens to something a
   * person still recognises — {@code acme-storefront-checkout} at 10 is {@code acme}, not {@code
   * acme-store}. A first word longer than the limit has nowhere to back off to and is cut hard.
   */
  private static String truncate(String slug, int maxLength) {
    String cut = slug.substring(0, maxLength);
    if (slug.charAt(maxLength) != '-') {
      int lastHyphen = cut.lastIndexOf('-');
      if (lastHyphen > 0) {
        cut = cut.substring(0, lastHyphen);
      }
    }
    return EDGES.matcher(cut).replaceAll("");
  }
}
