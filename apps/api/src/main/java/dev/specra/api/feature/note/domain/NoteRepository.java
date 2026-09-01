package dev.specra.api.feature.note.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Queries only. "Find it or raise a 404" is a rule about the API, not about storage, so it lives in
 * {@code NoteService.require} — and a {@code default} method here would be intercepted and return
 * null the moment this interface is mocked, which is a silent way to make a unit test lie.
 */
public interface NoteRepository extends JpaRepository<Note, UUID> {

  /**
   * The casts on {@code :q} are load-bearing, not decoration.
   *
   * <p>{@code concat} is polymorphic, so PostgreSQL has nothing to infer an untyped parameter from.
   * With no search term it picks {@code bytea}, and the statement dies on {@code function
   * lower(bytea) does not exist} — a 500 on the plain, unfiltered listing, which is the request the
   * UI makes most. {@code cast(:q as string)} tells it what the parameter is before it guesses.
   *
   * <p>{@code :tag} needs no cast: it is compared against a typed column, which is inference
   * enough.
   */
  @Query(
      """
      select distinct n from Note n
      left join n.tags t
      where (:q is null or lower(n.title) like lower(concat('%', cast(:q as string), '%'))
                        or lower(n.content) like lower(concat('%', cast(:q as string), '%')))
        and (:tag is null or t = :tag)
      """)
  Page<Note> search(@Param("q") String q, @Param("tag") String tag, Pageable pageable);
}
