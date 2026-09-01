package dev.specra.api.feature.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A person with an account.
 *
 * <p>{@code passwordHash} is nullable because V2 declared it so: an account that only ever arrives
 * through SSO never gets one, and a column that is not null would make that unrepresentable before
 * the feature exists. {@link #canSignInWithPassword()} is what callers ask instead of testing for
 * null themselves.
 *
 * <p>The lockout counters live here rather than in a side table because they are read on the same
 * row the sign-in already loads, and a failed attempt has to be written before the answer goes out
 * — an extra table would be an extra write on the hottest unauthenticated path in the API.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id @GeneratedValue private UUID id;

  /** Stored lowercased; {@code ux_users_email_lower} is what enforces that it is also unique. */
  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "password_hash", length = 200)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status = UserStatus.ACTIVE;

  /** Null means "answer in whatever language the request asks for". */
  @Column(length = 10)
  private String locale;

  @Column(name = "failed_logins", nullable = false)
  private int failedLogins;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public boolean canSignInWithPassword() {
    return passwordHash != null && !passwordHash.isBlank();
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /** A lockout that has run out is not a lockout; nothing has to clear it for that to be true. */
  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void recordSuccessfulSignIn(Instant now) {
    this.failedLogins = 0;
    this.lockedUntil = null;
    this.lastLoginAt = now;
  }

  /**
   * Counts the attempt and locks the account once it reaches the threshold. Returns true when this
   * attempt is the one that locked it, which is what decides whether the caller is told to wait.
   */
  public boolean recordFailedSignIn(int maxAttempts, Instant lockedUntil) {
    this.failedLogins++;
    if (this.failedLogins >= maxAttempts) {
      this.lockedUntil = lockedUntil;
      this.failedLogins = 0;
      return true;
    }
    return false;
  }
}
