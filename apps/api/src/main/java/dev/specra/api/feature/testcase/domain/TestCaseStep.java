package dev.specra.api.feature.testcase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One ordered, human-language step of a manual test case.
 *
 * <p>A child of {@link TestCase} in every sense: created and replaced through it, deleted with it,
 * and never addressed by its own endpoint. An edit rewrites the whole list — the steps are the text
 * of the case, not records with identity worth preserving across edits. What later stages trace
 * back to is the {@code position}, which survives a rewrite; the row id does not.
 */
@Entity
@Table(name = "test_case_steps")
@Getter
@Setter
@NoArgsConstructor
public class TestCaseStep {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "test_case_id", nullable = false)
  private TestCase testCase;

  /** 1-based, dense — the number the user sees and the IR traces to. */
  @Column(nullable = false)
  private int position;

  @Column(name = "action_text", nullable = false, columnDefinition = "text")
  private String actionText;

  @Column(name = "test_data", columnDefinition = "text")
  private String testData;

  @Column(name = "expected_text", columnDefinition = "text")
  private String expectedText;
}
