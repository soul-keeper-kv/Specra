package dev.specra.api.feature.testmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TestManagementConnectionRequest(
    @NotBlank(message = "{validation.test-management.connection.name.required}") @Size(max = 120, message = "{validation.test-management.connection.name.size}") String name,
    @NotBlank(message = "{validation.test-management.connection.provider.required}") @Size(max = 64, message = "{validation.test-management.connection.provider.size}") String provider,
    @NotEmpty(message = "{validation.test-management.connection.configuration.required}") Map<String, String> configuration,
    @NotEmpty(message = "{validation.test-management.connection.credentials.required}") Map<String, String> credentials) {}
