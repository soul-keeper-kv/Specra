package dev.specra.api.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Uniform error body for every non-2xx response.")
public record ApiError(
    @Schema(example = "2026-01-01T00:00:00Z") Instant timestamp,
    @Schema(example = "404") int status,
    @Schema(example = "Not Found") String error,
    @Schema(example = "Note 7f3c… not found") String message,
    @Schema(example = "/api/notes/7f3c") String path,
    @Schema(description = "Field-level validation failures, when the status is 400.")
        Map<String, String> fieldErrors) {

  public static ApiError of(int status, String error, String message, String path) {
    return new ApiError(Instant.now(), status, error, message, path, Map.of());
  }
}
