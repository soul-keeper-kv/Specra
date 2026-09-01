package dev.specra.api.core.content;

import dev.specra.api.core.web.PageResponse;
import jakarta.validation.Valid;
import java.util.Set;

/**
 * The port every kind of user content is reached through.
 *
 * <p>Today the only implementation is backed by the {@code notes} table. Tomorrow it might be
 * uploaded files, a wiki, or a third-party workspace — and nothing above this interface has to
 * change, because callers hold a {@code ContentStore}, never a {@code NoteService}. That is the
 * whole point: the AI tools in particular must not know where content lives, or adding a second
 * storage backend means rewriting the prompts and the tools with it.
 *
 * <p>Implementations declare what they can do in {@link #capabilities()}. The four mutating methods
 * default to refusing, so a read-only store is a class with three methods and no dead overrides.
 *
 * <p>To add a store: implement this, return a new {@link #kind()}, annotate the class
 * {@code @Component} and {@code @Validated}. {@link ContentStoreRegistry} discovers it from the
 * context — there is no list to register it in.
 */
public interface ContentStore {

  /** Stable, lower-case discriminator; unique across the context. Also the config value. */
  String kind();

  Set<ContentCapability> capabilities();

  /** Newest first. Never returns full bodies — see {@link ContentSummary}. */
  PageResponse<ContentSummary> search(ContentQuery query);

  /**
   * @throws dev.specra.api.core.error.ResourceNotFoundException if no document has that id, or the
   *     id is not in a shape this store issues
   */
  ContentDocument get(String id);

  default ContentDocument create(@Valid ContentDraft draft) {
    throw new UnsupportedContentOperationException(kind(), ContentCapability.CREATE);
  }

  default ContentDocument update(String id, @Valid ContentDraft draft) {
    throw new UnsupportedContentOperationException(kind(), ContentCapability.UPDATE);
  }

  default void delete(String id) {
    throw new UnsupportedContentOperationException(kind(), ContentCapability.DELETE);
  }
}
