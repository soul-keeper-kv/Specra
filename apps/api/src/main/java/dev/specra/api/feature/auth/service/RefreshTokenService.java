package dev.specra.api.feature.auth.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.error.UnauthorizedException;
import dev.specra.api.feature.auth.domain.RefreshToken;
import dev.specra.api.feature.auth.domain.RefreshTokenRepository;
import dev.specra.api.feature.auth.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issues, rotates and revokes refresh tokens — which is to say, it is where a session begins and
 * ends.
 *
 * <p>Three decisions are worth knowing before changing anything here.
 *
 * <p><b>Only a hash is stored.</b> The value handed to the client is 256 bits of {@link
 * SecureRandom}; the row keeps its SHA-256. A database dump is therefore not a set of working
 * sessions. The hash is deliberately unsalted and unstretched, unlike a password: there is no
 * dictionary to defend against, and the lookup has to be one indexed equality.
 *
 * <p><b>Every use rotates.</b> Refreshing revokes the presented row and issues a successor, so a
 * token's useful life is one call rather than thirty days.
 *
 * <p><b>Reuse revokes the family.</b> A token that was already exchanged being presented again
 * means two parties hold it, and there is no way to tell which one is the user. Both are signed
 * out. That is the standard answer, and it is why {@code replacedBy} is recorded rather than the
 * row simply being deleted.
 */
@Service
@Transactional(readOnly = true)
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository repository;
  private final SecureRandom random = new SecureRandom();
  private final Duration lifetime;
  private final Clock clock;

  /** For the one write that has to outlive the exception thrown immediately after it. */
  private final TransactionTemplate newTransaction;

  public RefreshTokenService(
      RefreshTokenRepository repository,
      PlatformTransactionManager transactions,
      SpecraProperties properties,
      Clock clock) {
    this.repository = repository;
    this.lifetime = Duration.ofDays(properties.security().jwt().refreshDays());
    this.clock = clock;
    this.newTransaction = new TransactionTemplate(transactions);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * The stored row and the value the client gets, which is the only time the two exist together.
   */
  public record Issued(RefreshToken row, String value) {}

  @Transactional
  public Issued issue(User user, ClientInfo client) {
    byte[] raw = new byte[TOKEN_BYTES];
    random.nextBytes(raw);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setTokenHash(hash(value));
    token.setExpiresAt(clock.instant().plus(lifetime));
    token.setUserAgent(client.userAgent());
    token.setClientIp(client.ip());
    return new Issued(repository.save(token), value);
  }

  /**
   * Exchanges a presented token for a fresh pair's refresh half, and returns the row it came from
   * so the caller can reach the user.
   *
   * <p>The successor is written first and the predecessor is pointed at it, so a crash between the
   * two leaves a revoked token and a usable one rather than a user with neither.
   */
  @Transactional
  public Issued rotate(String presented, ClientInfo client) {
    RefreshToken current = requireUsable(presented);
    Instant now = clock.instant();

    User user = current.getUser();
    Issued next = issue(user, client);

    current.revoke(now);
    current.setLastUsedAt(now);
    current.setReplacedBy(next.row().getId());
    repository.save(current);

    return next;
  }

  /** Signing out one device. Presenting an unknown or already-revoked token is not an error. */
  @Transactional
  public void revoke(String presented) {
    repository
        .findByTokenHash(hash(presented))
        .ifPresent(
            token -> {
              token.revoke(clock.instant());
              repository.save(token);
            });
  }

  @Transactional
  public int revokeAllForUser(UUID userId) {
    return repository.revokeAllForUser(userId, clock.instant());
  }

  public List<RefreshToken> activeSessions(UUID userId) {
    return repository.findActive(userId, clock.instant());
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionId) {
    RefreshToken token =
        repository
            .findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("resource.session", sessionId));
    token.revoke(clock.instant());
    repository.save(token);
  }

  /** True when this is the row the caller is currently holding — what marks "this device". */
  public boolean matches(RefreshToken token, String presented) {
    return presented != null && token.getTokenHash().equals(hash(presented));
  }

  private RefreshToken requireUsable(String presented) {
    RefreshToken token =
        repository.findByTokenHash(hash(presented)).orElseThrow(RefreshTokenService::invalid);
    Instant now = clock.instant();

    if (token.isRevoked()) {
      // Already exchanged, and here it is again: either the user's copy or somebody else's, and
      // nothing in the request says which. Ending every session is the only answer that is right
      // in both cases.
      //
      // In its own transaction, because the very next line throws — and an exception rolls the
      // caller's transaction back, revocations included. Detecting a replay and then undoing the
      // response to it is worse than not detecting it, because it looks handled.
      UUID userId = token.getUser().getId();
      log.warn(
          "Refresh token {} was presented after it had been revoked; signing out user {}",
          token.getId(),
          userId);
      newTransaction.executeWithoutResult(status -> repository.revokeAllForUser(userId, now));
      throw invalid();
    }
    if (token.isExpiredAt(now)) {
      throw invalid();
    }
    return token;
  }

  private static UnauthorizedException invalid() {
    return new UnauthorizedException(ErrorCode.INVALID_TOKEN, ErrorCode.INVALID_TOKEN.detailKey());
  }

  /** Hex rather than base64 so the column is a fixed 64 characters and comparisons are exact. */
  static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JRE", e);
    }
  }
}
