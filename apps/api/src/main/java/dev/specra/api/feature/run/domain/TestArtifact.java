package dev.specra.api.feature.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A piece of evidence, by reference.
 *
 * <p>The bytes are in object storage and reach a browser through a short-lived signed URL: the API
 * never proxies them and Postgres never holds them. {@code expiresAt} is not decoration — traces
 * are large and a QA team runs a great many tests.
 */
@Entity
@Table(name = "test_artifacts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TestArtifact {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "test_run_item_id", nullable = false)
  private TestRunItem item;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ArtifactKind kind;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "content_type", length = 120)
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "expires_at")
  private Instant expiresAt;

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
