package dev.specra.api.feature.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One issued refresh token — which is to say, one signed-in device.
 *
 * <p>The token itself is never stored: {@code tokenHash} is a SHA-256 of the value the client
 * holds, so this table is useless to anyone who reads it. Hashing is unsalted and unstretched on
 * purpose, unlike a password: the input is 256 bits of {@code SecureRandom}, so there is nothing
 * for a dictionary to try, and the lookup has to be a single indexed equality.
 *
 * <p>{@code replacedBy} is what makes rotation more than hygiene. A refresh revokes the row it came
 * from and records its successor, so a token presented after it has already been exchanged is not
 * merely stale — it is a copy, and {@code RefreshTokenService} answers that by revoking the whole
 * family rather than the one row.
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by")
  private UUID replacedBy;

  @Column(name = "user_agent", length = 256)
  private String userAgent;

  @Column(name = "client_ip", length = 64)
  private String clientIp;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public boolean isUsableAt(Instant now) {
    return !isRevoked() && !isExpiredAt(now);
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      this.revokedAt = now;
    }
  }
}
