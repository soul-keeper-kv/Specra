package dev.specra.api.feature.codegen.service;

import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import java.util.Locale;

/**
 * The commit message a generation writes when the reviewer does not replace it.
 *
 * <p>Conventional Commits, because that is what this repository enforces on itself and what the
 * user's repository most likely expects: {@code test(auth): generate login spec from TC-104}. One
 * approved proposal is one commit, so the message describes exactly that.
 */
final class CommitMessages {

  private static final int MAX_SUBJECT = 72;

  private CommitMessages() {}

  static String forGeneration(TestCaseResponse testCase) {
    String scope =
        testCase.tags().stream().sorted().findFirst().map(CommitMessages::slug).orElse(null);
    String subject = "generate %s from %s".formatted(specName(testCase), testCase.reference());
    String prefix = scope == null ? "test: " : "test(%s): ".formatted(scope);
    return truncate(prefix + subject);
  }

  /** The test's own words, lowercased — the message says what was generated, not how. */
  private static String specName(TestCaseResponse testCase) {
    String title = testCase.title().trim();
    return title.isEmpty() ? "spec" : title.toLowerCase(Locale.ROOT);
  }

  private static String slug(String tag) {
    return tag.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
  }

  /** commitlint's default subject limit; a message nobody can read is a message nobody reads. */
  private static String truncate(String subject) {
    return subject.length() <= MAX_SUBJECT ? subject : subject.substring(0, MAX_SUBJECT - 1) + "…";
  }
}
