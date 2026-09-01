package dev.specra.api.core.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The closed set of machine-readable error codes this API can return.
 *
 * <p>Clients branch on {@code code}, never on the human text: the text is translated and will
 * differ between callers, the code will not. Each constant owns its HTTP status and derives its
 * {@code type} URI and message keys from its own name, so adding a case cannot leave the three out
 * of sync.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 — Problem Details for HTTP
 *     APIs</a>
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  CONFLICT(HttpStatus.CONFLICT),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  /** The model provider answered, but refused the request (bad key, quota, unknown model). */
  AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
  /** The model provider could not be reached at all, or timed out. */
  AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  /** The store or backend named by the request exists, but cannot perform this operation. */
  UNSUPPORTED_OPERATION(HttpStatus.NOT_IMPLEMENTED),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  /** Dereferenceable in principle, stable in practice — clients match on it as an opaque id. */
  private static final String TYPE_PREFIX = "https://specra.dev/problems/";

  private final HttpStatus status;
  private final String slug;

  ErrorCode(HttpStatus status) {
    this.status = status;
    this.slug = name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  public HttpStatus status() {
    return status;
  }

  /** Kebab-case form: what goes on the wire as {@code code} and inside the {@code type} URI. */
  public String slug() {
    return slug;
  }

  public URI type() {
    return URI.create(TYPE_PREFIX + slug);
  }

  /** Short summary, translated. Same for every occurrence of this code. */
  public String titleKey() {
    return "error." + slug + ".title";
  }

  /** Longer, occurrence-specific explanation. Translated, and may take arguments. */
  public String detailKey() {
    return "error." + slug + ".detail";
  }

  /** Fallback for framework exceptions this enum does not name explicitly. */
  public static ErrorCode forStatus(HttpStatusCode status) {
    for (ErrorCode code : values()) {
      if (code.status.value() == status.value()) {
        return code;
      }
    }
    return status.is4xxClientError() ? INVALID_PARAMETER : INTERNAL_ERROR;
  }
}
