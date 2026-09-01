package dev.specra.api.core.content;

import dev.specra.api.config.SpecraProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Finds the right {@link ContentStore} for a request. The factory half of the abstraction.
 *
 * <p>Selection follows the same pattern Spring AI uses for model providers, and for the same
 * reason: every implementation sits in the context, and configuration — not a branch in code —
 * decides which one is the default. Swapping storage is {@code CONTENT_DEFAULT_KIND=file} and a
 * restart; adding storage is one new {@code @Component}, with nothing here to edit.
 *
 * <p>Both failure modes are caught at startup rather than at the first call: two stores claiming
 * one kind, or a configured default that no store answers to.
 */
@Component
public class ContentStoreRegistry {

  private static final Logger log = LoggerFactory.getLogger(ContentStoreRegistry.class);

  private final Map<String, ContentStore> byKind;
  private final String defaultKind;

  public ContentStoreRegistry(List<ContentStore> stores, SpecraProperties properties) {
    Map<String, ContentStore> index = new LinkedHashMap<>();
    for (ContentStore store : stores) {
      ContentStore clash = index.put(normalise(store.kind()), store);
      if (clash != null) {
        throw new IllegalStateException(
            "Two ContentStore beans both claim kind '%s': %s and %s"
                .formatted(store.kind(), clash.getClass().getName(), store.getClass().getName()));
      }
    }
    this.byKind = Map.copyOf(index);
    this.defaultKind = normalise(properties.content().defaultKind());

    if (!byKind.containsKey(defaultKind)) {
      // Fail the boot, not the first tool call: a typo here is otherwise invisible until a
      // user asks the assistant a question and gets a 400 nobody can explain.
      throw new IllegalStateException(
          "specra.content.default-kind is '%s', but the registered kinds are %s"
              .formatted(defaultKind, byKind.keySet()));
    }
    log.info("Content stores registered: {} (default '{}')", byKind.keySet(), defaultKind);
  }

  /** The store used when the caller does not name one. */
  public ContentStore defaultStore() {
    return byKind.get(defaultKind);
  }

  /** A blank or {@code null} kind resolves to the default, which is what a tool call omits. */
  public ContentStore require(@Nullable String kind) {
    if (kind == null || kind.isBlank()) {
      return defaultStore();
    }
    ContentStore store = byKind.get(normalise(kind));
    if (store == null) {
      throw new UnknownContentKindException(kind, kinds());
    }
    return store;
  }

  /**
   * Resolve and check in one step. Callers that are about to write should use this, so an
   * unsupported operation is refused before any partial work happens.
   */
  public ContentStore requireCapable(@Nullable String kind, ContentCapability capability) {
    ContentStore store = require(kind);
    if (!store.capabilities().contains(capability)) {
      throw new UnsupportedContentOperationException(store.kind(), capability);
    }
    return store;
  }

  public Set<String> kinds() {
    return byKind.keySet();
  }

  private static String normalise(String kind) {
    return kind.strip().toLowerCase(Locale.ROOT);
  }
}
