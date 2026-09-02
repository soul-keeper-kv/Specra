package dev.specra.api.feature.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One model call that could change something, recorded before anything is applied.
 *
 * <p>An audit line and a proposal in one row: which model, at what cost, about which subject, with
 * what outcome — and, once a person has decided, who and when. Immutable apart from the decision,
 * so there is no {@code updated_at} and no lock column.
 */
@Entity
@Table(name = "ai_generations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AiGeneration {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private AiGenerationKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AiGenerationStatus status;

  @Column(name = "subject_type", nullable = false, length = 32, updatable = false)
  private String subjectType;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;

  @Column(name = "result_id")
  private UUID resultId;

  @Column(name = "input_checksum", nullable = false, length = 64, updatable = false)
  private String inputChecksum;

  @Column(length = 64)
  private String provider;

  @Column(length = 120)
  private String model;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(columnDefinition = "text")
  private String diff;

  @Column(columnDefinition = "text")
  private String rationale;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
