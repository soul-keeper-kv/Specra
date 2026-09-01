package dev.specra.api.core.git;

import java.util.List;

/**
 * The working copy's state against its branch and its remote.
 *
 * @param ahead commits the copy has that the remote does not; what a push would publish
 * @param behind commits the remote has that the copy does not; what a pull would fetch
 */
public record GitStatus(String branch, int ahead, int behind, List<FileChange> changes) {

  public boolean clean() {
    return changes.isEmpty();
  }
}
