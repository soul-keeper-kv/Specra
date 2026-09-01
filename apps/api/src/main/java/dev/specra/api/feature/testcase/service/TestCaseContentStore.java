package dev.specra.api.feature.testcase.service;

import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentDocument;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.content.ContentStore;
import dev.specra.api.core.content.ContentSummary;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.testcase.dto.TestCaseResponse;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Test cases, seen through the generic content port — what keeps the assistant working now that the
 * notes scaffold is gone.
 *
 * <p>Read-only where the note store was writable, and that is a product decision, not a shortcut: a
 * test case authored through a chat draft would be a title and a blob with no steps, and "AI
 * proposes, a human disposes" means the way a model helps write one is the modelling pipeline with
 * a person approving — never a quiet write from the middle of a conversation.
 */
@Component
@Validated
public class TestCaseContentStore implements ContentStore {

  public static final String KIND = "testcase";

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

  private final TestCaseService testCases;

  public TestCaseContentStore(TestCaseService testCases) {
    this.testCases = testCases;
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public Set<ContentCapability> capabilities() {
    return EnumSet.of(ContentCapability.READ);
  }

  @Override
  public PageResponse<ContentSummary> search(ContentQuery query) {
    Pageable pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
    return testCases
        .searchAll(query.text(), query.tag(), pageable)
        .map(TestCaseContentStore::toSummary);
  }

  @Override
  public ContentDocument get(String id) {
    return toDocument(testCases.get(parseId(id)));
  }

  /**
   * An id this store could never have issued names nothing, so it is a 404 rather than a 400 — one
   * consistent answer for "no such document", and no reply advertising that ids are UUIDs.
   */
  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id.strip());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ResourceNotFoundException("resource.testcase", id);
    }
  }

  private static ContentDocument toDocument(TestCaseResponse testCase) {
    return new ContentDocument(
        KIND,
        testCase.id().toString(),
        testCase.reference() + " — " + testCase.title(),
        TestCaseText.compose(testCase),
        testCase.tags(),
        testCase.createdAt(),
        testCase.updatedAt());
  }

  private static ContentSummary toSummary(TestCaseResponse testCase) {
    return new ContentSummary(
        KIND,
        testCase.id().toString(),
        testCase.reference() + " — " + testCase.title(),
        ContentSummary.excerpt(TestCaseText.compose(testCase)),
        testCase.tags(),
        testCase.updatedAt());
  }
}
