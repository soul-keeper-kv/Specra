package dev.specra.api.feature.pageobject.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * A page of the application under test, and the locators inspection found on it.
 *
 * <p>This is the row 02-test-model-ir.md is arranged around. An IR target is {@code {page,
 * element}} and nothing else, so when the application renames a test id, one element here changes
 * and every test case referencing it regenerates correctly. Putting the selector in the IR would
 * scatter the same fact across every case that touches the button.
 */
@Entity
@Table(name = "page_objects")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PageObject {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  /** What the IR calls this page — `LoginPage`. Unique per project. */
  @Column(nullable = false, length = 120)
  private String name;

  /** The path inspection opened, so it can be re-inspected without asking again. */
  @Column(length = 500)
  private String route;

  /** Null until a real page has been read. The UI shows "never inspected" from this. */
  @Column(name = "inspected_at")
  private Instant inspectedAt;

  /**
   * Cascaded and orphan-removed, because an element has no meaning apart from its page and a
   * re-inspection replaces the whole set.
   */
  @OneToMany(
      mappedBy = "pageObject",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("name ASC")
  private List<PageElement> elements = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  /**
   * Swaps in a fresh set of elements without replacing the collection.
   *
   * <p>Mutating in place rather than assigning is what stops Hibernate deleting and reinserting
   * every row on each inspection — the same trap {@code Note.replaceTags} documents.
   */
  public void replaceElements(List<PageElement> replacements) {
    elements.clear();
    for (PageElement element : replacements) {
      element.setPageObject(this);
      element.setWorkspaceId(workspaceId);
      elements.add(element);
    }
  }
}
