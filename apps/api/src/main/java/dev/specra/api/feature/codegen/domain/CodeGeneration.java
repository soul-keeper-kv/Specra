package dev.specra.api.feature.codegen.domain;

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
 * Generated code, proposed and not yet anywhere near a branch.
 *
 * <p>The files are held as JSON because they have no other home yet: Git owns code that exists, and
 * this is code that does not. Applying moves them into the working copy and commits them, after
 * which {@code automation_files} keeps the path and the hash and these bodies are history.
 */
@Entity
@Table(name = "code_generations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CodeGeneration {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  @Column(name = "test_case_id", nullable = false, updatable = false)
  private UUID testCaseId;

  /** Null for a FIX: a repair patches committed code rather than projecting an IR version. */
  @Column(name = "test_model_id", updatable = false)
  private UUID testModelId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private GenerationKind kind = GenerationKind.CODE;

  /** The reading this repairs. Null for an ordinary CODE generation. */
  @Column(name = "failure_analysis_id", updatable = false)
  private UUID failureAnalysisId;

  @Column(name = "generation_id", nullable = false, updatable = false)
  private UUID generationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CodeGenerationStatus status = CodeGenerationStatus.PROPOSED;

  @Column(name = "adapter_version", nullable = false, length = 32, updatable = false)
  private String adapterVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private String files;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private String unresolved = "[]";

  @Column(name = "commit_sha", length = 64)
  private String commitSha;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;
}
