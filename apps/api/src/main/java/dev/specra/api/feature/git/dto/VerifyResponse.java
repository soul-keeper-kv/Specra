package dev.specra.api.feature.git.dto;

import java.util.List;

/** Proof the connection works: what the remote itself advertised. */
public record VerifyResponse(String defaultBranch, List<String> branches) {}
