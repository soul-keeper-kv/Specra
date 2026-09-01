package dev.specra.api.feature.git.domain;

import java.util.Locale;

/**
 * Which host a repository lives on. The V2 check constraint enumerates the same three; an
 * implementation exists for GitHub today, and asking for one of the others is answered with a
 * problem document, not a crash — the enum is the vocabulary, the beans are the capability.
 */
public enum GitProviderKind {
  GITHUB,
  GITLAB,
  BITBUCKET;

  /** Lower-case form: what {@code GitProvider.kind()} returns and configuration names. */
  public String kind() {
    return name().toLowerCase(Locale.ROOT);
  }
}
