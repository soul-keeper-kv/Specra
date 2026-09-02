package dev.specra.api.feature.testmanagement.service;

import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testmanagement.dto.ExternalTestDetail;
import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.dto.TestManagementVerifyResponse;
import java.util.Map;

/** Provider boundary: Specra consumes tests; Xray, TestRail and others own them. */
public interface TestManagementProvider {
  String kind();

  void validate(Map<String, String> configuration, Map<String, String> credentials);

  TestManagementVerifyResponse verify(ProviderConnection connection, String remoteProjectId);

  /**
   * One page of the provider's tests, filtered by a free-text {@code query} the provider
   * interprets. Paged rather than a bare list: the answer to "the test is not in the first 20" has
   * to be a next page, and the total is what tells the caller whether one exists.
   */
  PageResponse<ExternalTestSummary> tests(
      ProviderConnection connection, String remoteProjectId, String query, int page, int size);

  ExternalTestDetail test(ProviderConnection connection, String remoteProjectId, String externalId);

  record ProviderConnection(Map<String, String> configuration, Map<String, String> credentials) {}
}
