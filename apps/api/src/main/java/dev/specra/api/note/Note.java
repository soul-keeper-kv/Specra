package dev.specra.api.note;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "notes")
@jakarta.persistence.EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Note {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "note_tags", joinColumns = @JoinColumn(name = "note_id"))
  @Column(name = "tag", nullable = false, length = 64)
  private Set<String> tags = new LinkedHashSet<>();

  /** Set once the note's chunks have been embedded into the pgvector store. */
  @Column(name = "indexed_at")
  private Instant indexedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public void replaceTags(Set<String> incoming) {
    // Mutate in place so Hibernate tracks the element collection instead of
    // dropping and recreating it.
    this.tags.clear();
    if (incoming != null) {
      incoming.stream()
          .filter(t -> t != null && !t.isBlank())
          .map(t -> t.trim().toLowerCase())
          .forEach(this.tags::add);
    }
  }
}
