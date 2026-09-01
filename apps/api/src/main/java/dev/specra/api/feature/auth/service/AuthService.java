package dev.specra.api.feature.auth.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ForbiddenException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.error.UnauthorizedException;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.domain.UserRepository;
import dev.specra.api.feature.auth.dto.AccountResponse;
import dev.specra.api.feature.auth.dto.AuthTokens;
import dev.specra.api.feature.auth.dto.ChangePasswordRequest;
import dev.specra.api.feature.auth.dto.LoginRequest;
import dev.specra.api.feature.auth.dto.ProfileRequest;
import dev.specra.api.feature.auth.dto.RegisterRequest;
import dev.specra.api.feature.auth.dto.SessionResponse;
import dev.specra.api.feature.auth.mapper.UserMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Everything an account does about itself: register, sign in, refresh, sign out, change a password,
 * list its devices.
 *
 * <p>It knows nothing about workspaces. Registration publishes {@link AuthEvents.UserRegistered}
 * and stops there, so the tenancy code can react without auth depending on it — see {@code
 * WorkspaceProvisioning}.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final UserMapper mapper;
  private final PasswordEncoder passwords;
  private final TokenService tokens;
  private final RefreshTokenService refreshTokens;
  private final ApplicationEventPublisher events;
  private final SpecraProperties.Security config;
  private final Clock clock;

  /**
   * A real hash of a value nobody knows, verified against when the address does not exist.
   *
   * <p>Without it, an unknown address answers in a fraction of the time a known one does, because
   * bcrypt only runs in the second case — and that difference is a working account enumerator. It
   * is computed at startup rather than held as a constant so it costs whatever this installation's
   * bcrypt strength costs, which is exactly the number that has to match.
   */
  private final String timingEqualiser;

  public AuthService(
      UserRepository users,
      UserMapper mapper,
      PasswordEncoder passwords,
      TokenService tokens,
      RefreshTokenService refreshTokens,
      ApplicationEventPublisher events,
      SpecraProperties properties,
      Clock clock) {
    this.users = users;
    this.mapper = mapper;
    this.passwords = passwords;
    this.tokens = tokens;
    this.refreshTokens = refreshTokens;
    this.events = events;
    this.config = properties.security();
    this.clock = clock;
    this.timingEqualiser = passwords.encode(UUID.randomUUID().toString());
  }

  // ── sign up and sign in ────────────────────────────────────────────────────

  @Transactional
  public AuthTokens register(RegisterRequest request, ClientInfo client) {
    if (!config.registrationOpen()) {
      throw new ForbiddenException("error.forbidden.registration-closed");
    }

    String email = normalise(request.email());
    if (users.existsByEmailIgnoringCase(email)) {
      throw new BusinessException(
          ErrorCode.EMAIL_ALREADY_USED, ErrorCode.EMAIL_ALREADY_USED.detailKey(), email);
    }

    User user = new User();
    user.setEmail(email);
    user.setDisplayName(request.displayName().trim());
    user.setPasswordHash(passwords.encode(request.password()));
    user.setLastLoginAt(clock.instant());
    User saved = users.save(user);

    log.info("Registered user {}", saved.getId());
    events.publishEvent(new AuthEvents.UserRegistered(saved.getId(), saved.getDisplayName()));
    return issue(saved, client);
  }

  /**
   * The one place a password is checked.
   *
   * <p>Every rejection that is about the credential — no such address, no password set on the
   * account, wrong password — comes back as one {@code invalid-credentials}, because anything more
   * specific tells a stranger which addresses have accounts here. A lock and a suspension are told
   * plainly: by then the caller has either proved they hold the account, or the answer is no use to
   * them anyway.
   */
  @Transactional
  public AuthTokens login(LoginRequest request, ClientInfo client) {
    Instant now = clock.instant();
    User user = users.findByEmailIgnoringCase(normalise(request.email())).orElse(null);

    if (user == null || !user.canSignInWithPassword()) {
      passwords.matches(request.password(), timingEqualiser);
      throw invalidCredentials();
    }
    if (user.isLockedAt(now)) {
      throw locked(user, now);
    }
    if (!user.isActive()) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_SUSPENDED, ErrorCode.ACCOUNT_SUSPENDED.detailKey());
    }

    if (!passwords.matches(request.password(), user.getPasswordHash())) {
      boolean nowLocked =
          user.recordFailedSignIn(
              config.lockout().maxAttempts(), now.plus(config.lockout().duration()));
      users.save(user);
      if (nowLocked) {
        log.warn("Locked account {} after repeated failed sign-ins", user.getId());
        throw locked(user, now);
      }
      throw invalidCredentials();
    }

    user.recordSuccessfulSignIn(now);
    return issue(users.save(user), client);
  }

  /**
   * Exchanges a refresh token for a new pair. The access token is built from the user row rather
   * than copied from the old one, so a renamed or suspended account is noticed here — which is what
   * makes the access token's short life the bound on how stale a grant can be.
   */
  @Transactional
  public AuthTokens refresh(String presented, ClientInfo client) {
    RefreshTokenService.Issued rotated = refreshTokens.rotate(presented, client);
    User user = rotated.row().getUser();

    if (!user.isActive()) {
      refreshTokens.revokeAllForUser(user.getId());
      throw new BusinessException(
          ErrorCode.ACCOUNT_SUSPENDED, ErrorCode.ACCOUNT_SUSPENDED.detailKey());
    }

    TokenService.AccessToken access = tokens.issue(user);
    return AuthTokens.bearer(
        access.value(),
        access.expiresAt(),
        rotated.value(),
        rotated.row().getExpiresAt(),
        mapper.toResponse(user));
  }

  @Transactional
  public void logout(String presented) {
    refreshTokens.revoke(presented);
  }

  // ── the account itself ─────────────────────────────────────────────────────

  public AccountResponse me() {
    return mapper.toResponse(require(CurrentUser.requireId()));
  }

  @Transactional
  public AccountResponse updateProfile(ProfileRequest request) {
    User user = require(CurrentUser.requireId());
    user.setDisplayName(request.displayName().trim());
    user.setLocale(StringUtils.hasText(request.locale()) ? request.locale().trim() : null);
    return mapper.toResponse(users.save(user));
  }

  /**
   * Changing a password signs every other device out, and re-issues this one's pair so the caller
   * is not signed out by their own action. A change that left the old sessions alive would be no
   * use to the person changing it because they believe they were compromised.
   */
  @Transactional
  public AuthTokens changePassword(ChangePasswordRequest request, ClientInfo client) {
    User user = require(CurrentUser.requireId());
    if (!user.canSignInWithPassword()
        || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
      throw invalidCredentials();
    }

    user.setPasswordHash(passwords.encode(request.newPassword()));
    users.save(user);

    int revoked = refreshTokens.revokeAllForUser(user.getId());
    log.info("Password changed for user {}; {} session(s) revoked", user.getId(), revoked);
    return issue(user, client);
  }

  /** The signed-in devices. {@code current} marks the one holding {@code presented}, if any. */
  public List<SessionResponse> sessions(String presented) {
    UUID userId = CurrentUser.requireId();
    return refreshTokens.activeSessions(userId).stream()
        .map(token -> mapper.toSession(token, refreshTokens.matches(token, presented)))
        .toList();
  }

  @Transactional
  public void revokeSession(UUID sessionId) {
    refreshTokens.revokeSession(CurrentUser.requireId(), sessionId);
  }

  /**
   * The one throw site for a user who is not there.
   *
   * <p>Public because the workspace feature adds people by looking them up, and that call has to
   * report a missing account the same way this one does.
   */
  public User require(UUID id) {
    return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("resource.user", id));
  }

  /** Adding a member is "find this address or say so", which is a different 404 from an id. */
  public User requireByEmail(String email) {
    return users
        .findByEmailIgnoringCase(normalise(email))
        .orElseThrow(() -> new ResourceNotFoundException("resource.user", email));
  }

  // ── plumbing ───────────────────────────────────────────────────────────────

  private AuthTokens issue(User user, ClientInfo client) {
    TokenService.AccessToken access = tokens.issue(user);
    RefreshTokenService.Issued refresh = refreshTokens.issue(user, client);
    return AuthTokens.bearer(
        access.value(),
        access.expiresAt(),
        refresh.value(),
        refresh.row().getExpiresAt(),
        mapper.toResponse(user));
  }

  private static String normalise(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static UnauthorizedException invalidCredentials() {
    return new UnauthorizedException(
        ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.detailKey());
  }

  /** The detail says how much longer, because "try again later" is not an answer. */
  private BusinessException locked(User user, Instant now) {
    long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
    return new BusinessException(
        ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.detailKey(), minutes);
  }
}
