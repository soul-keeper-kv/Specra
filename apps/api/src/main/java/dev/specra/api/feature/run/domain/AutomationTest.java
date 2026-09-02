package dev.specra.api.feature.run.domain;

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
 * The identity of a generated test inside the repository: which file holds it, under what title, at
 * which commit.
 *
 * <p>Written for the first time here, though the table has existed since V5, and it is worth saying
 * why it is not {@code code_generations}. That table is the history of proposals — one row per
 * attempt, with what a person decided. This is the *current* state: a run has to answer "which spec
 * do I execute for this test case", which no proposal row can, because several of them may have
 * been applied and superseded.
 *
 * <p>It is also why deleting generated code does not delete intent: the test case is the intent and
 * outlives this row, which is only where its projection currently lives (01-domain-model.md).
 */
@Entity
@Table(name = "automation_tests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AutomationTest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "test_case_id", nullable = false, updatable = false)
  private UUID testCaseId;

  /** Repo-relative path of the spec file. */
  @Column(name = "spec_path", nullable = false, length = 500)
  private String specPath;

  /** The `test(...)` title — the other half of a test's identity inside a file. */
  @Column(name = "test_title", nullable = false, length = 300)
  private String testTitle;

  /** The IR version this projection came from, so drift is detectable. */
  @Column(name = "current_model_id")
  private UUID currentModelId;

  @Column(name = "last_commit_sha", length = 64)
  private String lastCommitSha;

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
