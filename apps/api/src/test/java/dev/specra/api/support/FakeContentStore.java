package dev.specra.api.support;

import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentDocument;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.content.ContentStore;
import dev.specra.api.core.content.ContentSummary;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.web.PageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An in-memory {@link ContentStore}, so the registry and the AI tools can be tested without a
 * database — and so "a second store exists" is something the tests actually exercise rather than
 * something the design merely claims.
 */
public class FakeContentStore implements ContentStore {

  private final String kind;
  private final Set<ContentCapability> capabilities;
  private final List<ContentDocument> documents = new ArrayList<>();

  public FakeContentStore(String kind, ContentCapability... capabilities) {
    this.kind = kind;
    this.capabilities = Set.of(capabilities);
  }

  /** Read-write store holding nothing. */
  public static FakeContentStore full(String kind) {
    return new FakeContentStore(kind, ContentCapability.values());
  }

  public FakeContentStore with(String id, String title, String body, String... tags) {
    documents.add(
        new ContentDocument(kind, id, title, body, Set.of(tags), Instant.EPOCH, Instant.EPOCH));
    return this;
  }

  @Override
  public String kind() {
    return kind;
  }

  @Override
  public Set<ContentCapability> capabilities() {
    return capabilities;
  }

  @Override
  public PageResponse<ContentSummary> search(ContentQuery query) {
    List<ContentDocument> matches = documents.stream().filter(d -> matches(d, query)).toList();
    List<ContentSummary> window =
        matches.stream()
            .skip((long) query.page() * query.size())
            .limit(query.size())
            .map(FakeContentStore::toSummary)
            .toList();
    int totalPages = (int) Math.ceil((double) matches.size() / query.size());
    return new PageResponse<>(
        window,
        query.page(),
        query.size(),
        matches.size(),
        totalPages,
        query.page() == 0,
        query.page() >= totalPages - 1);
  }

  @Override
  public ContentDocument get(String id) {
    return documents.stream()
        .filter(d -> d.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("resource.note", id));
  }

  private static boolean matches(ContentDocument document, ContentQuery query) {
    boolean byText =
        query.text() == null
            || (document.title() + " " + document.body())
                .toLowerCase(Locale.ROOT)
                .contains(query.text().toLowerCase(Locale.ROOT));
    boolean byTag = query.tag() == null || document.tags().contains(query.tag());
    return byText && byTag;
  }

  private static ContentSummary toSummary(ContentDocument document) {
    return new ContentSummary(
        document.kind(),
        document.id(),
        document.title(),
        ContentSummary.excerpt(document.body()),
        document.tags(),
        document.updatedAt());
  }
}
