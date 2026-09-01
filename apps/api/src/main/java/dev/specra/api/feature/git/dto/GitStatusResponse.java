package dev.specra.api.feature.git.dto;

import dev.specra.api.core.git.FileChange;
import java.util.List;

/**
 * @param ahead commits a push would publish
 * @param behind commits a pull would fetch
 */
public record GitStatusResponse(
    String branch, int ahead, int behind, boolean clean, List<Change> changes) {

  public record Change(String path, FileChange.ChangeKind kind) {}
}
