package dev.specra.api.feature.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * The tenant boundary. Everything a user can see hangs off exactly one of these, and every table
 * below it carries {@code workspace_id} directly rather than reaching it through a join.
 */
@Entity
@Table(name = "workspaces")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Workspace {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  /** URL-safe, unique across the installation, and stable once created. */
  @Column(nullable = false, length = 64)
  private String slug;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
