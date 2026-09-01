package dev.specra.api.core.git;

/**
 * A decrypted credential, in memory for the length of one provider call and nowhere else.
 *
 * <p>Deliberately excluded from {@code toString} being useful: never log this record.
 *
 * @param username what the host pairs with the token; GitHub accepts any non-empty value for PATs
 */
public record GitAuth(String username, String token) {

  /** Keeps the token out of accidental log lines; the fields are still readable by name. */
  @Override
  public String toString() {
    return "GitAuth[username=" + username + ", token=***]";
  }
}
