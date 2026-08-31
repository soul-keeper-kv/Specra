package dev.specra.api.note;

import dev.specra.api.note.dto.NoteRequest;
import dev.specra.api.note.dto.NoteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Tags are deliberately excluded: {@link Note#replaceTags} mutates the element collection in place
 * so Hibernate does not delete and reinsert every row on each update.
 */
@Mapper(componentModel = "spring")
public interface NoteMapper {

  NoteResponse toResponse(Note note);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "indexedAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  Note toEntity(NoteRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "indexedAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  void update(@MappingTarget Note note, NoteRequest request);
}
