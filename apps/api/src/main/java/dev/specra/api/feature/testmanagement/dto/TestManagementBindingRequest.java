package dev.specra.api.feature.testmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TestManagementBindingRequest(
    @NotNull(message = "{validation.test-management.binding.connection.required}") UUID connectionId,
    @NotBlank(message = "{validation.test-management.binding.remote-project.required}") @Size(max = 200, message = "{validation.test-management.binding.remote-project.size}") String remoteProjectId) {}
