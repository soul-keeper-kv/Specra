package dev.specra.api.feature.run.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One cell of the matrix: one test, on one browser.
 *
 * <p>{@code failedStepId} is an IR step id, not a line number. That is what lets the UI highlight
 * the manual step the user wrote and the generated line at the same time, and it survives the file
 * being regenerated — a line number would not.
 */
@Entity
@Table(name = "test_run_items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TestRunItem {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "test_run_id", nullable = false)
  private TestRun run;

  /** Null once the automation test is deleted; the run keeps its history regardless. */
  @Column(name = "automation_test_id")
  private UUID automationTestId;

  @Column(name = "test_case_id", nullable = false)
  private UUID testCaseId;

  /** Repo-relative, so a result can be matched back to the file that produced it. */
  @Column(name = "spec_path", nullable = false, length = 500)
  private String specPath;

  @Column(nullable = false, length = 32)
  private String browser;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ItemStatus status = ItemStatus.QUEUED;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "failed_step_id", length = 64)
  private String failedStepId;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "error_type", length = 64)
  private String errorType;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @OneToMany(
      mappedBy = "item",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("kind ASC")
  private List<TestArtifact> artifacts = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public void addArtifact(TestArtifact artifact) {
    artifact.setItem(this);
    artifact.setWorkspaceId(workspaceId);
    artifacts.add(artifact);
  }
}
