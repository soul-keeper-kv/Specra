package dev.specra.api.core.security;

import java.util.Locale;

/**
 * What a signed-in user is allowed to do inside one workspace.
 *
 * <p>Permissions are code and roles are data: this enum is the closed set of capabilities the API
 * actually checks, and a role is a named bundle of them held against a membership row. That split
 * is what lets an authorisation decision be read off one lookup instead of assembled from a join,
 * and it is why adding a capability is a compile error at every site that has to decide it rather
 * than a row somebody forgot to insert.
 *
 * <p>Deliberately not a table. Enterprise RBAC — custom roles, per-resource grants, inheritance —
 * is off the roadmap ({@code docs/architecture/09-roadmap.md}); the day it arrives, roles become
 * rows and this enum stays exactly as it is, as the vocabulary those rows point at.
 *
 * <p>It lives in {@code core} because it is the shared authorisation vocabulary: {@code
 * WorkspaceRole} maps roles onto it, and every feature under a workspace asks for one by name.
 */
public enum Permission {
  /** See the workspace at all. Every member has it; it is what membership means. */
  WORKSPACE_VIEW,
  WORKSPACE_UPDATE,
  WORKSPACE_DELETE,

  MEMBER_VIEW,
  MEMBER_ADD,
  MEMBER_UPDATE_ROLE,
  MEMBER_REMOVE,

  /** Projects, test cases, environments — everything the workspace holds. */
  CONTENT_VIEW,
  CONTENT_EDIT,
  CONTENT_DELETE;

  private final String slug = name().toLowerCase(Locale.ROOT).replace('_', '-');

  /** Kebab-case: what goes on the wire, so the web app can key its UI off the same names. */
  public String slug() {
    return slug;
  }

  /** Bundle key for this permission's human name, substituted into "you need X" details. */
  public String messageKey() {
    return "permission." + slug;
  }
}
