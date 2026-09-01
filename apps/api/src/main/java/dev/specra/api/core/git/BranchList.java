package dev.specra.api.core.git;

import java.util.List;

/**
 * What the remote advertises: its branches and which one HEAD points at. The answer to "did the
 * connection actually work", which is why verify returns it rather than a bare 200.
 */
public record BranchList(String defaultBranch, List<String> names) {}
