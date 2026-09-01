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

  @Query(
      """
      select distinct n from Note n
      left join n.tags t
      where (:q is null or lower(n.title) like lower(concat('%', :q, '%'))
                        or lower(n.content) like lower(concat('%', :q, '%')))
        and (:tag is null or t = :tag)
      """)
  Page<Note> search(@Param("q") String q, @Param("tag") String tag, Pageable pageable);
}
