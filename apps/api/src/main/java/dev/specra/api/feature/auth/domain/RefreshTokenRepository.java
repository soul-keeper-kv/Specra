package dev.specra.api.feature.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** The hash, not the token: the caller has already hashed what the client presented. */
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** The "active sessions" list — revoked and expired rows are history, not devices. */
  @Query(
      """
      select t from RefreshToken t
      where t.user.id = :userId and t.revokedAt is null and t.expiresAt > :now
      order by t.createdAt desc
      """)
  List<RefreshToken> findActive(@Param("userId") UUID userId, @Param("now") Instant now);

  Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Signs every device out at once — what a password change and a detected token replay both do. A
   * bulk update rather than a loop because it must be one statement: two clients refreshing while
   * this runs should both lose, and a read-modify-write would let one of them win.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update RefreshToken t set t.revokedAt = :now
      where t.user.id = :userId and t.revokedAt is null
      """)
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

  /**
   * Rows that can never be presented again. Kept out of the sign-in path and run on a schedule: the
   * table is append-only otherwise, and a device that signed in a year ago is not evidence anyone
   * needs.
   */
  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :before")
  int deleteExpiredBefore(@Param("before") Instant before);
}
