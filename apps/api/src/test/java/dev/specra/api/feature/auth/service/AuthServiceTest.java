package dev.specra.api.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.feature.auth.domain.RefreshToken;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.domain.UserRepository;
import dev.specra.api.feature.auth.domain.UserStatus;
import dev.specra.api.feature.auth.dto.LoginRequest;
import dev.specra.api.feature.auth.dto.RegisterRequest;
import dev.specra.api.feature.auth.mapper.UserMapperImpl;
import dev.specra.api.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sign-in, and the three ways it is allowed to say no.
 *
 * <p>The password encoder is a stand-in rather than bcrypt: every assertion here is about which
 * branch was taken, and paying for a real key-derivation on each one buys nothing. The clock is
 * fixed, which is what lets a lockout be asserted without sleeping through it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-01T10:00:00Z");
  private static final int MAX_ATTEMPTS = 3;

  /** Reversible on purpose: a test encoder only has to be consistent. */
  private static final PasswordEncoder ENCODER =
      new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
          return "encoded:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
          return encoded != null && encoded.equals("encoded:" + raw);
        }
      };

  @Mock UserRepository users;
  @Mock TokenService tokens;
  @Mock RefreshTokenService refreshTokens;

  final List<Object> published = new ArrayList<>();

  AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            users,
            new UserMapperImpl(),
            ENCODER,
            tokens,
            refreshTokens,
            published::add,
            TestProperties.withLockout(MAX_ATTEMPTS, Duration.ofMinutes(15)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    when(tokens.issue(any()))
        .thenReturn(new TokenService.AccessToken("access", NOW.plusSeconds(900)));
    when(refreshTokens.issue(any(), any()))
        .thenAnswer(invocation -> new RefreshTokenService.Issued(refreshRow(), "refresh"));
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void registeringStoresAHashAndAnnouncesTheNewAccount() {
    when(users.existsByEmailIgnoringCase("qa@specra.dev")).thenReturn(false);

    var tokenPair =
        service.register(
            new RegisterRequest("  QA@Specra.dev ", "Nguyen QA", "correct horse battery"),
            ClientInfo.UNKNOWN);

    assertThat(tokenPair.accessToken()).isEqualTo("access");
    // Normalised on the way in, so the address cannot be registered twice in different cases.
    assertThat(tokenPair.user().email()).isEqualTo("qa@specra.dev");
    assertThat(published).singleElement().isInstanceOf(AuthEvents.UserRegistered.class);
  }

  @Test
  void anAddressThatAlreadyHasAnAccountIsItsOwnError() {
    when(users.existsByEmailIgnoringCase("qa@specra.dev")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.register(
                    new RegisterRequest("qa@specra.dev", "QA", "correct horse battery"),
                    ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_USED);

    verify(users, never()).save(any());
  }

  /** An unknown address and a wrong password must be indistinguishable from the outside. */
  @Test
  void anUnknownAddressAnswersExactlyLikeAWrongPassword() {
    when(users.findByEmailIgnoringCase("nobody@specra.dev")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.login(
                    new LoginRequest("nobody@specra.dev", "whatever"), ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  void aCorrectPasswordClearsTheFailureCountAndRecordsTheSignIn() {
    User user = existingUser();
    user.setFailedLogins(2);
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));

    service.login(new LoginRequest("qa@specra.dev", "right"), ClientInfo.UNKNOWN);

    assertThat(user.getFailedLogins()).isZero();
    assertThat(user.getLastLoginAt()).isEqualTo(NOW);
  }

  /** The attempt that reaches the threshold locks, and says so rather than repeating itself. */
  @Test
  void repeatedFailuresLockTheAccountAndTheAnswerChanges() {
    User user = existingUser();
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));
    LoginRequest wrong = new LoginRequest("qa@specra.dev", "nope");

    for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
      assertThatThrownBy(() -> service.login(wrong, ClientInfo.UNKNOWN))
          .isInstanceOf(BusinessException.class)
          .extracting(error -> ((BusinessException) error).errorCode())
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    assertThatThrownBy(() -> service.login(wrong, ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.ACCOUNT_LOCKED);

    assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  /** While locked, even the right password is refused — otherwise the lock throttles nothing. */
  @Test
  void aLockedAccountIsRefusedEvenWithTheRightPassword() {
    User user = existingUser();
    user.setLockedUntil(NOW.plusSeconds(60));
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.login(new LoginRequest("qa@specra.dev", "right"), ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
  }

  /** A lock that has run out is not a lock, and nothing has to clear it for that to be true. */
  @Test
  void anExpiredLockDoesNotStandInTheWay() {
    User user = existingUser();
    user.setLockedUntil(NOW.minusSeconds(1));
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));

    assertThat(service.login(new LoginRequest("qa@specra.dev", "right"), ClientInfo.UNKNOWN))
        .isNotNull();
  }

  @Test
  void aSuspendedAccountIsToldSoRatherThanBeingLeftGuessing() {
    User user = existingUser();
    user.setStatus(UserStatus.SUSPENDED);
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.login(new LoginRequest("qa@specra.dev", "right"), ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
  }

  /** An SSO-only account has no hash; that must be a refusal, never a match against null. */
  @Test
  void anAccountWithNoPasswordCannotBeSignedIntoWithOne() {
    User user = existingUser();
    user.setPasswordHash(null);
    when(users.findByEmailIgnoringCase("qa@specra.dev")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.login(new LoginRequest("qa@specra.dev", "right"), ClientInfo.UNKNOWN))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).errorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  private static User existingUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("qa@specra.dev");
    user.setDisplayName("QA");
    user.setPasswordHash(ENCODER.encode("right"));
    return user;
  }

  private static RefreshToken refreshRow() {
    RefreshToken token = new RefreshToken();
    token.setExpiresAt(NOW.plus(Duration.ofDays(30)));
    return token;
  }
}
