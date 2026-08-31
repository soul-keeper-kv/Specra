package dev.specra.api.core.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * Documentation-only mirror of the error body.
 *
 * <p>The wire format is Spring's {@link org.springframework.http.ProblemDetail} plus the extensions
 * {@link ProblemFactory} attaches. That type is assembled at runtime by an exception handler, so
 * springdoc never sees it on a method signature and cannot describe it. This record exists purely
 * so the OpenAPI schema — and therefore the web app's generated TypeScript — knows the shape.
 *
 * <p>If you add a property in {@code ProblemFactory}, add it here too.
 */
@Schema(
    name = "ApiProblem",
    description =
        "RFC 9457 problem document. Branch on `code`, never on `title` or `detail`: those are "
            + "translated per request and will differ between callers.")
public record ApiProblem(
    @Schema(
            description = "Stable, dereferenceable identifier for this class of error",
            example = "https://specra.dev/problems/resource-not-found")
        String type,
    @Schema(description = "Short summary, translated", example = "Not found") String title,
    @Schema(example = "404") int status,
    @Schema(
            description = "Explanation of this occurrence, translated",
            example = "Note 7f3c1b2e-… does not exist.")
        String detail,
    @Schema(description = "The request path that failed", example = "/api/notes/7f3c1b2e")
        String instance,
    @Schema(
            description = "Machine-readable error code. This is the field clients switch on.",
            example = "resource-not-found")
        String code,
    @Schema(example = "2026-01-01T00:00:00Z") Instant timestamp,
    @Schema(
            description = "Correlates with the API log line for this request",
            example = "0af7651916cd43dd8448eb211c80319c")
        String traceId,
    @Schema(description = "Echo of the X-Request-Id header", example = "8f14e45f-ceea-467a-a3c9")
        String requestId,
    @Schema(
            description = "Present only when code is `validation-failed`; keyed by field name",
            example = "{\"title\":\"Title is required\"}")
        Map<String, String> fieldErrors) {}
