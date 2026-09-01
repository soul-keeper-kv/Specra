package dev.specra.api.feature.project.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  /**
   * The cast on {@code :q} is load-bearing: {@code concat} is polymorphic, so with no search term
   * PostgreSQL infers {@code bytea} for an untyped parameter and the statement dies on {@code
   * lower(bytea) does not exist} — on the plain listing, which is the request the UI makes most.
   */
  @Query(
      """
      select p from Project p
      where p.workspaceId = :workspaceId
        and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
                        or lower(p.key) like lower(concat('%', cast(:q as string), '%')))
      """)
  Page<Project> search(
      @Param("workspaceId") UUID workspaceId, @Param("q") String q, Pageable pageable);

  boolean existsByWorkspaceIdAndKey(UUID workspaceId, String key);

  /**
   * The project row, locked for the rest of the transaction. What makes the test-case sequence a
   * sequence: two transactions bumping it serialise here instead of both reading the same number.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id = :id")
  Optional<Project> lockById(@Param("id") UUID id);

  Optional<Project> findByWorkspaceIdAndKey(UUID workspaceId, String key);
}
