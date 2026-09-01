package dev.specra.api.feature.testmanagement.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Discovers provider implementations; adding TestRail does not change the orchestration service.
 */
@Component
public class TestManagementProviders {
  private final Map<String, TestManagementProvider> providers;

  public TestManagementProviders(List<TestManagementProvider> providers) {
    this.providers =
        providers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    provider -> provider.kind().toLowerCase(Locale.ROOT), Function.identity()));
  }

  public TestManagementProvider require(String kind) {
    TestManagementProvider provider = providers.get(kind.toLowerCase(Locale.ROOT));
    if (provider == null) {
      throw new BusinessException(
          ErrorCode.UNSUPPORTED_OPERATION,
          "error.test-management.unknown-provider",
          kind,
          providers.keySet());
    }
    return provider;
  }
}
