package dev.specra.api.core.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The writable part of a document — what a caller supplies to create or replace one.
 *
 * <p>The constraints are here rather than on each store's own request type because the caller may
 * be a language model, which will happily invent an empty title. Implementations are
 * {@code @Validated}, so a bad draft becomes a 400 with {@code fieldErrors} rather than a
 * constraint violation deep inside JPA.
 *
 * @param title short human label
 * @param body the full text; what gets chunked and embedded for retrieval
 * @param tags free-form labels, normalised to lower case by the store
 */
public record ContentDraft(
    @NotBlank(message = "{validation.content.title.required}") @Size(max = 200, message = "{validation.content.title.size}") String title,
    @NotBlank(message = "{validation.content.body.required}") String body,
    Set<@Size(max = 64, message = "{validation.content.tag.size}") String> tags) {

  public ContentDraft {
    tags = tags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tags));
  }

  public ContentDraft(String title, String body) {
    this(title, body, Set.of());
  }
}
