package dev.specra.api.feature.environment.domain;

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
import java.util.LinkedHashMap;
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
 * Where a run points: a base URL and the variables the generated code reads.
 *
 * <p>The same spec runs against DEV and STAGING because nothing about the target is in the code —
 * {@code baseUrl} comes from here, and credentials arrive as environment variables the fixtures
 * read. That is what makes a generated project portable rather than pinned to one deployment.
 *
 * <p>Variables are a {@code @OneToMany} rather than an id reference because they have no life of
 * their own: nothing outside this aggregate addresses a variable, and deleting the environment
 * deletes them. Cross-<em>feature</em> references stay plain ids; this is inside one feature.
 */
@Entity
@Table(name = "environments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Environment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, updatable = false)
  private UUID projectId;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(name = "base_url", nullable = false, length = 500)
  private String baseUrl;

  /** Which one a run uses when the request does not name one. At most one per project. */
  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @OneToMany(
      mappedBy = "environment",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("key ASC")
  private List<EnvironmentVariable> variables = new ArrayList<>();

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
   * Makes the variable list match {@code incoming}, reusing the row that already holds each key.
   *
   * <p>Clearing and re-adding would be simpler and wrong: {@code (environment_id, key)} is unique,
   * and within one flush Hibernate issues the inserts before the orphan deletes, so re-saving an
   * environment with the same variable names fails on the constraint. Updating in place means a
   * kept key is an UPDATE, a dropped one is a DELETE, and only a genuinely new key is an INSERT —
   * which is also what keeps the row's id, and therefore anything referencing it, stable.
   */
  public void replaceVariables(List<EnvironmentVariable> incoming) {
    Map<String, EnvironmentVariable> existing = new LinkedHashMap<>();
    for (EnvironmentVariable variable : variables) {
      existing.put(variable.getKey(), variable);
    }

    List<EnvironmentVariable> next = new ArrayList<>();
    for (EnvironmentVariable wanted : incoming) {
      EnvironmentVariable row = existing.get(wanted.getKey());
      if (row == null) {
        row = wanted;
        row.setEnvironment(this);
        row.setWorkspaceId(workspaceId);
      } else {
        row.setValue(wanted.getValue());
        row.setSecret(wanted.isSecret());
      }
      next.add(row);
    }

    // Mutated in place rather than reassigned, so Hibernate keeps tracking this collection.
    variables.clear();
    variables.addAll(next);
  }
}
