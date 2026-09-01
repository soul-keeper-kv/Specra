package dev.specra.api.feature.ai.tool;

import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentDocument;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.content.ContentStore;
import dev.specra.api.core.content.ContentStoreRegistry;
import dev.specra.api.core.content.ContentSummary;
import dev.specra.api.core.web.PageResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * What the assistant is allowed to do with the user's content. Read-only, for now.
 *
 * <p>Spring AI turns each {@code @Tool} method into a function declaration, offers it to the model,
 * and — when the model asks for it — invokes the method and feeds the result back. No loop is
 * written by hand.
 *
 * <p>The tools go through {@link ContentStoreRegistry}, never through a concrete service, so
 * pointing the assistant at a different backing store is configuration rather than a rewrite.
 *
 * <p>Two deliberate omissions:
 *
 * <ul>
 *   <li><b>No create, update or delete.</b> Those land once the read path has been watched in
 *       practice; the port already supports them, so it is a matter of adding methods here.
 *   <li><b>No {@code kind} parameter.</b> One store is registered, so naming it in the schema would
 *       only give the model something to get wrong. When a second one exists, add the parameter and
 *       pass it to {@code require} — the registry already resolves blank to the default.
 * </ul>
 *
 * <p>Descriptions are English and live in the annotation rather than the message bundles. They are
 * prompt text read by a model, not UI text read by a user — the same reason {@code
 * specra.ai.system-prompt} sits in configuration.
 */
@Component
public class ContentTools {

  private static final Logger log = LoggerFactory.getLogger(ContentTools.class);

  private final ContentStoreRegistry stores;

  public ContentTools(ContentStoreRegistry stores) {
    this.stores = stores;
  }

  /**
   * @param totalMatches how many documents matched in total, which is usually more than were
   *     returned — the model needs it to say "showing 10 of 84" instead of implying it saw them all
   */
  public record SearchResult(long totalMatches, List<ContentSummary> results) {}

  @Tool(
      name = "search_content",
      description =
          """
          Search the user's saved documents by free text and/or an exact tag. \
          Returns short excerpts plus the id of each match; call get_content with an id \
          to read a document in full. Call this before answering any question about what \
          the user has written, rather than guessing.\
          """)
  public SearchResult searchContent(
      @ToolParam(
              required = false,
              description =
                  "Words to match against title and body. Omit to list the newest documents.")
          @Nullable String query,
      @ToolParam(required = false, description = "Exact tag to filter by. Omit for no tag filter.")
          @Nullable String tag,
      @ToolParam(required = false, description = "How many results to return. 1-50, default 10.")
          @Nullable Integer limit) {
    ContentStore store = stores.requireCapable(null, ContentCapability.READ);
    ContentQuery contentQuery = ContentQuery.of(query, tag, limit);

    PageResponse<ContentSummary> page = store.search(contentQuery);
    // Every tool call is audited. The line carries the request's traceId through the MDC, so a
    // surprising answer can be traced back to exactly what the model looked at.
    log.info(
        "tool search_content kind={} query={} tag={} limit={} -> {} of {}",
        store.kind(),
        contentQuery.text(),
        contentQuery.tag(),
        contentQuery.size(),
        page.content().size(),
        page.totalElements());
    return new SearchResult(page.totalElements(), page.content());
  }

  @Tool(
      name = "get_content",
      description =
          """
          Read one saved document in full, using an id returned by search_content. \
          Use it when an excerpt is not enough to answer.\
          """)
  public ContentDocument getContent(
      @ToolParam(description = "The id from a search_content result.") String id) {
    ContentStore store = stores.requireCapable(null, ContentCapability.READ);
    log.info("tool get_content kind={} id={}", store.kind(), id);
    return store.get(id);
  }
}
