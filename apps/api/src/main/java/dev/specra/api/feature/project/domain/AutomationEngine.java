package dev.specra.api.feature.project.domain;

/**
 * Which runner a project's generated code targets.
 *
 * <p>One value today, and the Test Model exists so a second one is a projection rather than a
 * rewrite. Naming it here is not the same as naming it in the IR: this is a project setting the
 * user chose, whereas an engine word inside a {@code TestModel} would tie the abstraction to the
 * tool it is meant to outlive.
 */
public enum AutomationEngine {
  PLAYWRIGHT
}
