package dev.specra.api.core.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Spring's {@code Page} serialises to an unstable shape and warns about it. This is the flat,
 * explicit envelope the web app's generated types are built from.
 */
@Schema(description = "A page of results.")
public record PageResponse<T>(
    List<T> content,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "137") long totalElements,
    @Schema(example = "7") int totalPages,
    boolean first,
    boolean last) {

  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }

  /**
   * Re-map the rows without touching the paging numbers. Lets an adapter translate one page of its
   * own type into a page of a shared type, which {@code PageResponse.from} cannot do because it
   * starts from a Spring {@code Page}.
   */
  public <R> PageResponse<R> map(Function<T, R> mapper) {
    return new PageResponse<>(
        content.stream().map(mapper).toList(), page, size, totalElements, totalPages, first, last);
  }
}
