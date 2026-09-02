package dev.specra.api.feature.environment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param key the name generated code reads through {@code process.env}, so it is constrained to
 *     what a process environment actually accepts rather than to anything a user might type
 * @param value null on a secret means "keep what is stored"; null on a plain variable means empty.
 *     A secret's value is never returned, so a form that round-trips a response has nothing to send
 *     back and must be able to say so.
 * @param secret whether the value is encrypted at rest and masked everywhere afterwards
 */
public record EnvironmentVariableRequest(
    @NotBlank(message = "{validation.environment.variable.key.required}") @Size(max = 120, message = "{validation.environment.variable.key.size}") @Pattern(
            regexp = "^[A-Za-z_][A-Za-z0-9_]*$",
            message = "{validation.environment.variable.key.format}")
        @Schema(example = "QA_USER_PASSWORD")
        String key,
    @Size(max = 4000, message = "{validation.environment.variable.value.size}") String value,
    @Schema(example = "true") Boolean secret) {

  public boolean isSecret() {
    return Boolean.TRUE.equals(secret);
  }
}
