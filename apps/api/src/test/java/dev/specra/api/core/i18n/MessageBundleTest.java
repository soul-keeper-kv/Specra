package dev.specra.api.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.core.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards the message bundles without booting Spring.
 *
 * <p>Missing translations are the classic way i18n rots: a key is added to the default bundle,
 * nobody adds it to the others, and the gap only shows up when a user in that language hits that
 * exact error. These three assertions catch it at build time instead.
 */
class MessageBundleTest {

  private static final String DEFAULT_BUNDLE = "/i18n/messages.properties";
  private static final String VI_BUNDLE = "/i18n/messages_vi.properties";

  @Test
  void everyErrorCodeHasATitleAndADetailInTheDefaultBundle() throws IOException {
    Properties bundle = load(DEFAULT_BUNDLE);

    Set<String> missing = new TreeSet<>();
    for (ErrorCode code : ErrorCode.values()) {
      if (!bundle.containsKey(code.titleKey())) {
        missing.add(code.titleKey());
      }
      if (!bundle.containsKey(code.detailKey())) {
        missing.add(code.detailKey());
      }
    }

    assertThat(missing)
        .as("ErrorCode constants with no text; add them to messages.properties")
        .isEmpty();
  }

  @Test
  void everyTranslationCoversEveryKeyOfTheDefaultBundle() throws IOException {
    Set<String> expected = keys(DEFAULT_BUNDLE);
    Set<String> actual = keys(VI_BUNDLE);

    assertThat(new TreeSet<>(expected).stream().filter(key -> !actual.contains(key)).toList())
        .as("keys missing from messages_vi.properties")
        .isEmpty();
    assertThat(new TreeSet<>(actual).stream().filter(key -> !expected.contains(key)).toList())
        .as("keys in messages_vi.properties that no longer exist in the default bundle")
        .isEmpty();
  }

  /**
   * A single quote is MessageFormat's escape character, so an apostrophe left undoubled silently
   * swallows the text after it — and only in the messages that take arguments, which is exactly
   * where the substitution then disappears.
   */
  @Test
  void parameterisedMessagesDoNotContainAnUnescapedApostrophe() throws IOException {
    for (String path : new String[] {DEFAULT_BUNDLE, VI_BUNDLE}) {
      Properties bundle = load(path);
      Set<String> offenders =
          bundle.stringPropertyNames().stream()
              .filter(key -> bundle.getProperty(key).contains("{"))
              .filter(key -> hasLoneApostrophe(bundle.getProperty(key)))
              .collect(Collectors.toCollection(TreeSet::new));

      assertThat(offenders).as("double the apostrophe in %s", path).isEmpty();
    }
  }

  private static boolean hasLoneApostrophe(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) != '\'') {
        continue;
      }
      boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == '\'';
      if (!doubled) {
        return true;
      }
      i++;
    }
    return false;
  }

  private static Set<String> keys(String path) throws IOException {
    return load(path).stringPropertyNames();
  }

  private static Properties load(String path) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = MessageBundleTest.class.getResourceAsStream(path)) {
      assertThat(in).as("bundle %s is on the classpath", path).isNotNull();
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
