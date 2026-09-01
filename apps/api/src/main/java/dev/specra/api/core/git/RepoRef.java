package dev.specra.api.core.git;

import org.springframework.lang.Nullable;

/**
 * Everything a provider needs to reach one remote: where it is, which branch is home, and what to
 * authenticate with — decrypted just in time by the caller and held only for the call.
 *
 * @param auth null for remotes that need none (public repos, {@code file://} fixtures in tests)
 */
public record RepoRef(String remoteUrl, String defaultBranch, @Nullable GitAuth auth) {}
