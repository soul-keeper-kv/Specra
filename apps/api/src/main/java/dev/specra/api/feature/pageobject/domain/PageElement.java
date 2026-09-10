package dev.specra.api.feature.pageobject.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One addressable element of a page, and the way to reach it.
 *
 * <p>{@code name} is what an IR target names, so it is the join between a test's intent and the
 * application's markup. The locator underneath it may change every week without a single test case
 * changing — that separation is the point of the whole table.
 */
@Entity
@Table(name = "page_elements")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PageElement {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "page_object_id", nullable = false)
  private PageObject pageObject;

  /** `submitButton` — the identifier an IR step's target uses. */
  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LocatorStrategy strategy = LocatorStrategy.CSS;

  @Column(nullable = false, length = 1000)
  private String value;

  /**
   * The accessible name a role needs to be unique.
   *
   * <p>Stored beside the strategy rather than folded into {@code value} because they are two
   * arguments in the generated call, and splitting a joined string back apart would be guesswork.
   */
  @Column(name = "qualifier", length = 500)
  private String qualifier;

  /**
   * The runner-up the planner scored.
   *
   * <p>Kept because failure analysis proposes it first when the primary drifts — the cheapest
   * possible repair is one the tool already knew about before anything broke.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "fallback_strategy", length = 32)
  private LocatorStrategy fallbackStrategy;

  @Column(name = "fallback_value", length = 1000)
  private String fallbackValue;

  /** 0–1, as the planner scored it. Shown so a person can distrust a weak locator. */
  @Column(precision = 3, scale = 2)
  private BigDecimal confidence;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  /**
   * Takes the locator a fresh inspection read for this same element.
   *
   * <p>Everything the planner produces is overwritten, including a locator a person set by hand.
   * That is deliberate: the markup under a name is expected to move — an application's UI changes
   * far more often than its test cases do — and a re-inspection is a person asking to be told where
   * it moved to. Keeping the old locator would leave the page object describing a page that no
   * longer exists, which is the failure this table exists to prevent.
   *
   * <p>{@code name} is not copied: it is what the two were matched on, and it is what the IR
   * references. Identity, audit columns and the version stay with the row.
   */
  void adopt(PageElement inspected) {
    this.strategy = inspected.getStrategy();
    this.value = inspected.getValue();
    this.qualifier = inspected.getQualifier();
    this.fallbackStrategy = inspected.getFallbackStrategy();
    this.fallbackValue = inspected.getFallbackValue();
    this.confidence = inspected.getConfidence();
  }
}
