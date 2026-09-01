package dev.specra.api.feature.testmanagement.service;

import dev.specra.api.feature.testmanagement.dto.ExternalTestSummary;
import dev.specra.api.feature.testmanagement.dto.TestManagementVerifyResponse;
import java.util.List;
import java.util.Map;

/** Provider boundary: Specra consumes tests; Xray, TestRail and others own them. */
public interface TestManagementProvider {
  String kind();

  void validate(Map<String, String> configuration, Map<String, String> credentials);

  TestManagementVerifyResponse verify(ProviderConnection connection, String remoteProjectId);

  List<ExternalTestSummary> tests(
      ProviderConnection connection, String remoteProjectId, String query, int page, int size);

  record ProviderConnection(Map<String, String> configuration, Map<String, String> credentials) {}
}
