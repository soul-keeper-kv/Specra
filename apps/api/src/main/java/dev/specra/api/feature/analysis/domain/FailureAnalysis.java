package dev.specra.api.feature.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One reading of one failed matrix cell.
 *
 * <p>Stored rather than recomputed on each view. The evidence is immutable once the run has
 * finished, the call costs money, and two people opening the same failure must see the same reading
 * — a second opinion that quietly contradicts the first is worse than none.
 */
@Entity
@Table(name = "failure_analyses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FailureAnalysis {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  @Column(name = "test_run_item_id", nullable = false, updatable = false)
  private UUID testRunItemId;

  @Column(name = "test_case_id", nullable = false, updatable = false)
  private UUID testCaseId;

  @Column(name = "generation_id", nullable = false)
  private UUID generationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "root_cause", nullable = false, length = 32)
  private RootCause rootCause = RootCause.UNKNOWN;

  @Column(nullable = false)
  private int confidence;

  @Column(nullable = false, columnDefinition = "text")
  private String summary;

  @Column(nullable = false, columnDefinition = "text")
  private String rationale;

  @Column(columnDefinition = "text")
  private String suggestion;

  /** What was actually put in front of the model, so the reading can be audited against it. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String evidence = "{}";

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;
}
