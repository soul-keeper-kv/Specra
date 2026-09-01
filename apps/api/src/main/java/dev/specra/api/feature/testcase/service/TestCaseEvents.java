package dev.specra.api.feature.testcase.service;

import java.util.UUID;

/**
 * What {@link TestCaseService} announces after it has changed a case.
 *
 * <p>Same contract as the notes scaffold had: plain CRUD publishes, whoever cares listens after
 * commit, and with the AI stack switched off nothing listens and CRUD still works. {@link
 * TestCaseIndexService} is today's listener, keeping the embeddings in step.
 */
public final class TestCaseEvents {

  private TestCaseEvents() {}

  /** The case is gone, so anything embedded from it is now unreachable content. */
  public record TestCaseDeleted(UUID testCaseId) {}

  /** The text changed, so any chunk already embedded is stale and must not stay retrievable. */
  public record TestCaseContentChanged(UUID testCaseId) {}
}
