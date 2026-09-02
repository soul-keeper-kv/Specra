package dev.specra.api.feature.project.domain;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One automation project, which is one Git repository and one default branch.
 *
 * <p>The workspace is held as a plain id rather than a {@code @ManyToOne}. Associations inside this
 * project's own feature are fine; one that reaches into another feature's entity would make the two
 * inseparable, and the column has to be here regardless — every table below a workspace carries
 * {@code workspace_id} so that tenancy filtering never depends on a join.
 */
@Entity
@Table(name = "projects")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Project {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  /** The handle a human reads out loud: the {@code TC} in {@code TC-104}. Immutable once set. */
  @Column(name = "key", nullable = false, updatable = false, length = 16)
  private String key;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AutomationEngine engine = AutomationEngine.PLAYWRIGHT;

  /**
   * Last minted number of the {@code TC-n} sequence. Bumped under a row lock — see {@code
   * ProjectService#nextTestCaseReference} — so two authors creating at once cannot share a number.
   */
  @Column(name = "test_case_sequence", nullable = false)
  private int testCaseSequence;

  /** The same, for {@code RUN-n}. A separate counter: they are separate things people quote. */
  @Column(name = "test_run_sequence", nullable = false)
  private int testRunSequence;

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
