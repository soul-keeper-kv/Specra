package dev.specra.api.feature.workspace.domain;

import dev.specra.api.core.security.Permission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A named bundle of {@link Permission}s, held against one membership row.
 *
 * <p>Three roles, matching the {@code ck_workspace_members_role} check constraint in V2 and the
 * model in {@code docs/architecture/01-domain-model.md}. The mapping is here rather than in a table
 * because it is a product decision, not tenant data: two installations disagreeing about what ADMIN
 * means is a support problem nobody has asked for.
 *
 * <p>Declaration order is seniority — {@link #outranks} reads it — so a new role goes in at its
 * rank, not at the end.
 */
public enum WorkspaceRole {

  /** The workspace is theirs: everything, including deleting it and handing it to someone else. */
  OWNER(EnumSet.allOf(Permission.class)),

  /**
   * Runs the workspace day to day. Everything except destroying it — an administrator who can
   * delete the tenant is an administrator whose mistake cannot be undone.
   */
  ADMIN(EnumSet.complementOf(EnumSet.of(Permission.WORKSPACE_DELETE))),

  /**
   * Does the work: sees the workspace and its people, reads and writes its content. Not allowed to
   * delete content, because in this product deletion also drops embeddings, history and generated
   * code that somebody else may be mid-review on.
   */
  MEMBER(
      EnumSet.of(
          Permission.WORKSPACE_VIEW,
          Permission.MEMBER_VIEW,
          Permission.CONTENT_VIEW,
          Permission.CONTENT_EDIT));

  private final Set<Permission> permissions;
  private final String slug;

  WorkspaceRole(Set<Permission> permissions) {
    this.permissions = Collections.unmodifiableSet(permissions);
    this.slug = name().toLowerCase(Locale.ROOT);
  }

  public Set<Permission> permissions() {
    return permissions;
  }

  public boolean can(Permission permission) {
    return permissions.contains(permission);
  }

  public String slug() {
    return slug;
  }

  /** The bundle key for this role's human name. */
  public String messageKey() {
    return "role." + slug;
  }

  /**
   * Strictly senior to {@code other}.
   *
   * <p>What stops an administrator from demoting an owner, or from granting a role above their own:
   * being allowed to change roles is not the same as being allowed to change <em>this</em> one.
   */
  public boolean outranks(WorkspaceRole other) {
    return ordinal() < other.ordinal();
  }

  public boolean outranksOrEquals(WorkspaceRole other) {
    return ordinal() <= other.ordinal();
  }
}
