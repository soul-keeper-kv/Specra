package dev.specra.api.feature.testcase.service;

import dev.specra.api.feature.ai.service.DocumentIndexService;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The one class that knows both a test case and the vector store — the successor of the notes
 * scaffold's {@code NoteIndexService}, with the same reasoning behind every choice: kept out of
 * {@link TestCaseService} so plain CRUD works with the AI stack off, reacting to events so the
 * dependency only points testcase → ai, and never {@code @Transactional} around the embedding round
 * trip.
 */
@Service
public class TestCaseIndexService {

  private static final Logger log = LoggerFactory.getLogger(TestCaseIndexService.class);

  private final TestCaseService testCases;
  private final DocumentIndexService documentIndex;

  public TestCaseIndexService(TestCaseService testCases, DocumentIndexService documentIndex) {
    this.testCases = testCases;
    this.documentIndex = documentIndex;
  }

  /**
   * Reading the case and marking it indexed are two short transactions of their own; the slow part
   * in between — the embedding round trip — holds no database connection.
   */
  public TestCaseResponse index(UUID id) {
    TestCaseResponse testCase = testCases.get(id);

    documentIndex.replaceDocument(
        testCase.id().toString(),
        TestCaseText.compose(testCase),
        Map.of(
            "testCaseId", testCase.id().toString(),
            "projectId", testCase.projectId().toString(),
            "reference", testCase.reference(),
            "title", testCase.title(),
            "tags", String.join(",", testCase.tags())));

    return testCases.markIndexed(id);
  }

  /** Idempotent: removing chunks that are already gone is a no-op. */
  public void removeFromIndex(UUID testCaseId) {
    documentIndex.deleteBySource(testCaseId.toString());
  }

  /** After commit, not before: a rolled-back delete must leave the case retrievable. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTestCaseDeleted(TestCaseEvents.TestCaseDeleted event) {
    removeQuietly(event.testCaseId());
  }

  /**
   * An edited case keeps its id but not its text; chunks embedded from the old text would keep
   * answering questions with content nobody can see. {@code indexedAt} is already null, so the case
   * simply shows as un-indexed until someone indexes it again.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTestCaseContentChanged(TestCaseEvents.TestCaseContentChanged event) {
    removeQuietly(event.testCaseId());
  }

  /**
   * The write the caller asked for has already committed, so failing here must not turn a
   * successful request into a 500 — the row is right, the index is behind, re-indexing fixes it.
   */
  private void removeQuietly(UUID testCaseId) {
    try {
      documentIndex.deleteBySource(testCaseId.toString());
    } catch (RuntimeException e) {
      log.error(
          "Could not drop embeddings for test case {} — the index is now stale", testCaseId, e);
    }
  }
}
