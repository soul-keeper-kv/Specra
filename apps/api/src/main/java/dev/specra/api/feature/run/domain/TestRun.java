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
 * One execution request: a selection of tests, over a browser matrix, against one environment.
 *
 * <p>It carries a {@code commitSha} rather than a pointer at "the working copy", because a result
 * that cannot be tied to an exact tree is not evidence — two people opening the same run must see
 * the same code. Running uncommitted work is allowed and honest about it: the sha is the base and
 * {@code dirtyDiff} is what was on top.
 */
@Entity
@Table(name = "test_runs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TestRun {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  /** {@code RUN-88} — minted per project, quoted by people, pasted into tickets. */
  @Column(nullable = false, updatable = false, length = 32)
  private String reference;

  @Column(name = "environment_id", nullable = false)
  private UUID environmentId;

  @Column(name = "commit_sha", nullable = false, length = 64)
  private String commitSha;

  @Column(name = "dirty_diff", columnDefinition = "text")
  private String dirtyDiff;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RunTrigger trigger = RunTrigger.MANUAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RunStatus status = RunStatus.QUEUED;

  @Column(name = "queued_at", nullable = false)
  private Instant queuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @OneToMany(
      mappedBy = "run",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("specPath ASC, browser ASC")
  private List<TestRunItem> items = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public void addItem(TestRunItem item) {
    item.setRun(this);
    item.setWorkspaceId(workspaceId);
    items.add(item);
  }
}
