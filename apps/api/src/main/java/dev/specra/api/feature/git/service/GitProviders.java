package dev.specra.api.feature.git.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.git.GitProvider;
import dev.specra.api.feature.git.domain.GitProviderKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Finds the {@link GitProvider} for a repository's host — discovered from the context, exactly like
 * {@code ContentStoreRegistry}, so a second host is a new bean and no {@code if} anywhere.
 *
 * <p>The enum on the repository row is the vocabulary; the beans are the capability. Asking for a
 * host with no bean is a problem document the user can act on, not a startup failure: a GitLab
 * value in the database must not stop GitHub users from working.
 */
@Component
public class GitProviders {

  private final Map<String, GitProvider> byKind = new LinkedHashMap<>();

  public GitProviders(List<GitProvider> providers) {
    for (GitProvider provider : providers) {
      byKind.put(provider.kind(), provider);
    }
  }

  public GitProvider require(GitProviderKind kind) {
    GitProvider provider = byKind.get(kind.kind());
    if (provider == null) {
      throw new BusinessException(
          ErrorCode.UNSUPPORTED_OPERATION, "error.git.provider-unavailable", kind.kind());
    }
    return provider;
  }
}
