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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
   * Cascaded and orphan-removed, because an element has no meaning apart from its page: dropping it
   * from this list during a re-inspection is what deletes the row.
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
   * Brings the set of elements in line with what inspection just read, matching on {@code name}.
   *
   * <p>Matched by name rather than cleared and refilled, and the difference is the whole method.
   * {@code uq_page_elements_name} is an immediate constraint, and Hibernate orders every INSERT
   * before every DELETE within a flush — so clearing the list and adding the same names back
   * inserted {@code emailInput} while the old {@code emailInput} was still on the table. The first
   * inspection of a page worked because the table was empty; every re-inspection afterwards failed
   * on a constraint, and the user was shown a bare 409 about a resource that had changed.
   *
   * <p>An element that is still on the page keeps its row and takes the new locator — the
   * application's markup is what moved, and inspection has just read where it moved to. An element
   * the page no longer has is dropped, so a page object cannot quietly accumulate locators for
   * things that stopped existing.
   */
  public void replaceElements(List<PageElement> replacements) {
    Map<String, PageElement> existing = new HashMap<>();
    for (PageElement element : elements) {
      existing.put(element.getName(), element);
    }

    List<PageElement> merged = new ArrayList<>(replacements.size());
    for (PageElement replacement : replacements) {
      PageElement element = existing.remove(replacement.getName());
      if (element == null) {
        element = replacement;
        element.setPageObject(this);
        element.setWorkspaceId(workspaceId);
      } else {
        element.adopt(replacement);
      }
      merged.add(element);
    }

    // Whatever is left in the map is no longer on the page. Emptying the collection and refilling
    // it from `merged` is safe where the original clear() was not: every surviving row is the same
    // instance Hibernate is already managing, so it is re-added rather than reinserted, and only
    // the genuinely absent ones are left orphaned for the DELETE.
    elements.clear();
    elements.addAll(merged);
  }
}
