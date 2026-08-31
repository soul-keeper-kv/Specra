package dev.specra.api.note;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
