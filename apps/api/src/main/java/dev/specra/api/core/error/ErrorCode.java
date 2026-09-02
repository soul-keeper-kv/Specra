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
  /**
   * Wrong email or wrong password — deliberately one code for both, so the response cannot be used
   * to find out which addresses have accounts. Declared after {@link #UNAUTHORIZED} because both
   * are 401 and {@link #forStatus} must keep returning the general one.
   */
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  /**
   * The access or refresh token is missing, expired, malformed, or has been revoked. The web app
   * branches on this to attempt one refresh before it gives up and sends the user to sign in.
   */
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
  /** Too many failed sign-in attempts; the account unlocks itself after a cooling-off period. */
  ACCOUNT_LOCKED(HttpStatus.LOCKED),
  /** An administrator suspended this account. Waiting will not help; a person has to act. */
  ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND),
  /**
   * The bound Jira/Xray project has no such test. A 404 and not an {@link
   * #INTEGRATION_PROVIDER_ERROR} 502: nothing upstream is broken — the key is wrong, invisible to
   * the stored token, or lives in another project. The caller can act on that; a 502 tells them
   * only to wait. Declared after {@link #RESOURCE_NOT_FOUND} so {@link #forStatus} keeps returning
   * the general 404.
   */
  EXTERNAL_TEST_NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  CONFLICT(HttpStatus.CONFLICT),
  /**
   * Registration hit an address that already has an account. A distinct code because the sign-up
   * form points at its own email field and offers "sign in instead", which a generic conflict
   * cannot do. After {@link #CONFLICT} for the same reason as above.
   */
  EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
  /**
   * Apply or reject on a generation that is no longer PROPOSED — already applied, rejected, or
   * superseded by a newer one. Declared after {@link #CONFLICT} for the usual reason.
   */
  GENERATION_NOT_PROPOSED(HttpStatus.CONFLICT),
  /**
   * The remote refused a non-fast-forward push. Resolved by the user pulling and retrying — never
   * by a force push or a background rebase they did not ask for. After {@link #CONFLICT} so {@link
   * #forStatus} keeps returning the general 409.
   */
  GIT_PUSH_REJECTED(HttpStatus.CONFLICT),
  /** A write wants the working copy, but uncommitted edits are sitting in it. */
  WORKING_COPY_DIRTY(HttpStatus.CONFLICT),
  /** A git operation on a project that has no repository connected yet. */
  REPOSITORY_NOT_CONNECTED(HttpStatus.CONFLICT),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  /**
   * The model produced an IR that failed schema, referential or semantic validation, even after the
   * one repair round the pipeline allows. The violations travel with the problem so the UI can
   * point at the step. Declared first of the 422s so {@link #forStatus} keeps the general one.
   */
  TEST_MODEL_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
  /**
   * Understanding could not turn a manual step into an action without guessing, and guessing is
   * exactly what it must not do. The questions travel with the problem; answering them in the test
   * case is the fix.
   */
  TEST_CASE_AMBIGUOUS(HttpStatus.UNPROCESSABLE_ENTITY),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  /** The model provider answered, but refused the request (bad key, quota, unknown model). */
  AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
  /**
   * The Git remote rejected our credentials. 502, not 401: the caller is authenticated with Specra
   * — it is Specra whose stored credential the remote turned away. Declared after {@link
   * #AI_PROVIDER_ERROR} so {@link #forStatus} keeps the general 502.
   */
  GIT_AUTH_FAILED(HttpStatus.BAD_GATEWAY),
  /** Jira/Xray accepted the request but rejected the stored PAT or its project permissions. */
  INTEGRATION_AUTH_FAILED(HttpStatus.BAD_GATEWAY),
  /** Jira/Xray answered with a failure unrelated to authentication. */
  INTEGRATION_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
  /** The model provider could not be reached at all, or timed out. */
  AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  /** The configured Jira/Xray host could not be reached or timed out. */
  INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  /**
   * The runner could not be reached, or did not answer in time. Ours to fix, unlike a refusal: the
   * user can only wait. After {@link #AI_PROVIDER_UNAVAILABLE} so {@link #forStatus} keeps
   * returning the general 503.
   */
  RUNNER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  /**
   * Nobody has given this installation usable credentials for the active model provider, so there
   * is nothing to call. Distinct from {@link #AI_PROVIDER_ERROR} on purpose: that one means the
   * provider said no, this one means we never got as far as asking. The client can act on it — the
   * fix is to add a key — so the UI offers that instead of reporting a failure.
   *
   * <p>Declared after {@link #AI_PROVIDER_UNAVAILABLE} deliberately: both are 503, and {@link
   * #forStatus} returns the first match, which must stay the general one.
   */
  AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),
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
