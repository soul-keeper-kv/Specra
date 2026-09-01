package dev.specra.api.feature.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Lowercased on both sides rather than relying on the caller: this is the query behind sign-in
   * and behind the uniqueness check, and the two must not be able to disagree about what "the same
   * address" means. It matches {@code ux_users_email_lower}, so the index is used.
   */
  @Query("select u from User u where lower(u.email) = lower(:email)")
  Optional<User> findByEmailIgnoringCase(@Param("email") String email);

  @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
  boolean existsByEmailIgnoringCase(@Param("email") String email);
}
