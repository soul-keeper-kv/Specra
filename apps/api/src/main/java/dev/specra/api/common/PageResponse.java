package dev.specra.api.common;

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
}
