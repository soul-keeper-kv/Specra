package dev.specra.api.feature.testcase.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The manual test case: intent, in human language.
 *
 * <p>This is the thing the whole product hangs off — the IR is derived from it, the code is derived
 * from the IR, and both can be regenerated as long as this row survives. It deliberately knows
 * nothing about models or code; those arrive as separate entities in later milestones and point
 * back at it.
 *
 * <p>The project and workspace are held as plain ids, not associations: an association across a
 * feature boundary drags that feature's entity into this one's every load, and {@code workspace_id}
 * has to be a real column here anyway so tenancy filtering never depends on a join.
 */
@Entity
@Table(name = "test_cases")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TestCase {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  /** The handle a human quotes: {@code TC-104}. Minted once from the project's sequence. */
  @Column(nullable = false, updatable = false, length = 32)
  private String reference;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(columnDefinition = "text")
  private String preconditions;

  @Column(name = "expected_result", columnDefinition = "text")
  private String expectedResult;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private TestCasePriority priority = TestCasePriority.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(name = "automation_status", nullable = false, length = 24)
  private AutomationStatus automationStatus = AutomationStatus.NOT_AUTOMATED;

  /** True when the text changed after the IR was generated; cleared by regeneration. */
  @Column(name = "out_of_date", nullable = false)
  private boolean outOfDate;

  @OneToMany(
      mappedBy = "testCase",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position asc")
  private List<TestCaseStep> steps = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "test_case_tags", joinColumns = @JoinColumn(name = "test_case_id"))
  @Column(name = "tag", nullable = false, length = 64)
  private Set<String> tags = new LinkedHashSet<>();

  /** Set once the case's text has been embedded into the pgvector store. */
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

  /**
   * Replaces the whole list, renumbering from 1. Mutating the managed collection in place keeps
   * orphan removal working; the deferred unique constraint on {@code (test_case_id, position)}
   * absorbs whatever order Hibernate flushes the rewrite in.
   */
  public void replaceSteps(List<TestCaseStep> incoming) {
    this.steps.clear();
    if (incoming == null) {
      return;
    }
    int position = 1;
    for (TestCaseStep step : incoming) {
      step.setTestCase(this);
      step.setWorkspaceId(this.workspaceId);
      step.setPosition(position++);
      this.steps.add(step);
    }
  }

  /** Same normalisation the notes scaffold used: trimmed, lower-cased, blanks dropped. */
  public void replaceTags(Set<String> incoming) {
    this.tags.clear();
    if (incoming != null) {
      incoming.stream()
          .filter(t -> t != null && !t.isBlank())
          .map(t -> t.trim().toLowerCase())
          .forEach(this.tags::add);
    }
  }
}
