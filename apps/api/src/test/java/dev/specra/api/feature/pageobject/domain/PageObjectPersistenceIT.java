package dev.specra.api.feature.pageobject.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-inspection against a real PostgreSQL, because the bug this covers only exists there.
 *
 * <p>{@code PageObjectServiceTest} mocks the repository, so it proves the merge picks the right
 * rows and nothing about whether the database accepts them. The failure was {@code
 * uq_page_elements_name} rejecting a flush — an INSERT for a name whose old row had not been
 * deleted yet — and no test with a mocked repository can ever see it. Every inspection after the
 * first one on a given page returned a 409 the user could do nothing about.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class PageObjectPersistenceIT {

  @Autowired PageObjectRepository repository;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManager entities;

  /**
   * The exact sequence a user performs: inspect a page, change the application, inspect it again.
   *
   * <p>Each inspection is its own transaction, as it is in production — the second one loads the
   * page object the first one wrote, rather than reusing a persistence context that never had to
   * talk to the database.
   */
  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
  void aSecondInspectionOfTheSamePageDoesNotBreakTheUniqueConstraint() {
    UUID workspace = UUID.randomUUID();
    UUID project = UUID.randomUUID();
    transactions.executeWithoutResult(status -> seedTenant(workspace, project));

    UUID id =
        transactions.execute(
            status -> {
              PageObject page = new PageObject();
              page.setWorkspaceId(workspace);
              page.setProjectId(project);
              page.setName("LoginPage");
              page.setRoute("/login");
              page.setInspectedAt(Instant.now());
              page.replaceElements(
                  List.of(
                      element("emailInput", LocatorStrategy.CSS, "#email"),
                      element("submitButton", LocatorStrategy.CSS, "#submit")));
              return repository.save(page).getId();
            });

    // The UI moved: the same two elements now carry test ids, and one of them is gone.
    transactions.executeWithoutResult(
        status -> {
          PageObject page = repository.findById(id).orElseThrow();
          page.replaceElements(
              List.of(
                  element("emailInput", LocatorStrategy.TEST_ID, "email"),
                  element("rememberMe", LocatorStrategy.TEST_ID, "remember")));
          repository.save(page);
          // Forces the flush inside the transaction, where the constraint used to fire.
          entities.flush();
        });

    transactions.executeWithoutResult(
        status -> {
          PageObject page = repository.findById(id).orElseThrow();
          assertThat(page.getElements())
              .extracting(PageElement::getName)
              .containsExactly("emailInput", "rememberMe");
          // The element that survived took the new locator: the markup moved, and inspection read
          // where it moved to.
          PageElement email =
              page.getElements().stream()
                  .filter(each -> each.getName().equals("emailInput"))
                  .findFirst()
                  .orElseThrow();
          assertThat(email.getStrategy()).isEqualTo(LocatorStrategy.TEST_ID);
          assertThat(email.getValue()).isEqualTo("email");
        });
  }

  /**
   * A workspace and a project for the page object to hang off.
   *
   * <p>Inserted directly rather than through the two services: both columns are real foreign keys,
   * and this test is about what happens to {@code page_elements} — going through project creation
   * would drag in membership and permissions to prove a constraint on another table.
   */
  private void seedTenant(UUID workspace, UUID project) {
    entities
        .createNativeQuery(
            "INSERT INTO workspaces (id, name, slug, created_at, updated_at)"
                + " VALUES (?1, 'Acme', ?2, now(), now())")
        .setParameter(1, workspace)
        .setParameter(2, "acme-" + workspace.toString().substring(0, 8))
        .executeUpdate();
    entities
        .createNativeQuery(
            "INSERT INTO projects (id, workspace_id, \"key\", name, engine, created_at, updated_at)"
                + " VALUES (?1, ?2, 'WEB', 'Web', 'PLAYWRIGHT', now(), now())")
        .setParameter(1, project)
        .setParameter(2, workspace)
        .executeUpdate();
  }

  private static PageElement element(String name, LocatorStrategy strategy, String value) {
    PageElement element = new PageElement();
    element.setName(name);
    element.setStrategy(strategy);
    element.setValue(value);
    element.setConfidence(BigDecimal.ONE);
    return element;
  }
}
