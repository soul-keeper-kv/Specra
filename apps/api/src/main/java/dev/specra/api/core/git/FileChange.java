package dev.specra.api.core.git;

/** One changed path in the working copy, with how it changed. */
public record FileChange(String path, ChangeKind kind) {

  public enum ChangeKind {
    ADDED,
    MODIFIED,
    DELETED,
    UNTRACKED,
    CONFLICTING
  }
}
