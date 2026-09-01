package dev.specra.api.feature.testmanagement.dto;

public record TestManagementVerifyResponse(
    String accountName, String remoteProjectId, String remoteProjectName, long testCount) {}
