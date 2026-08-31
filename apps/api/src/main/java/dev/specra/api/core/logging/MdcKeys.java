package dev.specra.api.core.logging;

/**
 * Every key this application puts into the SLF4J {@link org.slf4j.MDC}.
 *
 * <p>The logback pattern in {@code logback-spring.xml} and the JSON fields in the {@code prod}
 * profile both read these names, so they are constants rather than string literals scattered
 * around. {@code traceId} and {@code spanId} are written by Micrometer Tracing, not by us — they
 * are named here so log configuration has one place to look.
 */
public final class MdcKeys {

  /** Correlates every log line of one HTTP request; echoed back in {@link #REQUEST_ID_HEADER}. */
  public static final String REQUEST_ID = "requestId";

  /** Written by Micrometer Tracing's observation handler; spans the whole distributed call. */
  public static final String TRACE_ID = "traceId";

  public static final String SPAN_ID = "spanId";

  public static final String HTTP_METHOD = "httpMethod";
  public static final String HTTP_PATH = "httpPath";
  public static final String CLIENT_IP = "clientIp";
  public static final String LOCALE = "locale";

  /** Accepted from the caller so a request can be followed across the web app and the API. */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  private MdcKeys() {}
}
