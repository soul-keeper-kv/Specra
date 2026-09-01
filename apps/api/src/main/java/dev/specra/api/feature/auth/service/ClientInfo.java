package dev.specra.api.feature.auth.service;

import org.springframework.lang.Nullable;

/**
 * What the request says about the device it came from, for the "signed-in devices" list.
 *
 * <p>A plain record rather than the request itself, so the service layer never sees {@code
 * HttpServletRequest} — and so a value that is entirely attacker-controlled is truncated once, on
 * the way in, instead of at every place it is stored or shown.
 *
 * @param userAgent raw {@code User-Agent}, truncated to what the column holds
 * @param ip remote address as the container reports it
 */
public record ClientInfo(@Nullable String userAgent, @Nullable String ip) {

  private static final int USER_AGENT_MAX = 256;
  private static final int IP_MAX = 64;

  public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

  public static ClientInfo of(@Nullable String userAgent, @Nullable String ip) {
    return new ClientInfo(clamp(userAgent, USER_AGENT_MAX), clamp(ip, IP_MAX));
  }

  @Nullable private static String clamp(@Nullable String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
