package dev.specra.api.feature.testmodel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One generation of a test case's IR (table {@code test_models}, V5).
 *
 * <p>Immutable once written: regeneration adds a row with the next {@code version}, and impact
 * analysis diffs the two. The document is stored as the JSON the schema validated, byte for byte,
 * so the checksum computed on the way in is the checksum of what comes back out.
 */
@Entity
@Table(name = "test_models")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TestModelVersion {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "test_case_id", nullable = false, updatable = false)
  private UUID testCaseId;

  @Column(nullable = false, updatable = false)
  private int version;

  @Column(name = "ir_version", nullable = false, updatable = false)
  private int irVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private String document;

  @Column(nullable = false, length = 64, updatable = false)
  private String checksum;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
