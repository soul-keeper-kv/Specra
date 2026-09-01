package dev.specra.api.feature.auth.domain;

/**
 * Whether this account may sign in at all.
 *
 * <p>Deliberately not the place where a lockout lives: a lockout is temporary, self-clearing and
 * caused by the user, while a suspension is a decision somebody made and only somebody can undo.
 * Folding the two into one column loses that difference, and the difference is the whole of what a
 * support answer has to say.
 */
public enum UserStatus {
  ACTIVE,
  SUSPENDED
}
