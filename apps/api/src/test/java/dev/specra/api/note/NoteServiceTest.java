package dev.specra.api.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.specra.api.common.NotFoundException;
import dev.specra.api.note.dto.NoteRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

  @Mock NoteRepository repository;

  NoteService service;

  @BeforeEach
  void setUp() {
    // The generated MapStruct implementation, not a mock: mapping bugs should fail here.
    service = new NoteService(repository, new NoteMapperImpl());
  }

  @Test
  void createNormalisesTags() {
    when(repository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        service.create(new NoteRequest("Title", "Body", Set.of("  Meeting ", "Q3", "", "  ")));

    assertThat(response.tags()).containsExactlyInAnyOrder("meeting", "q3");
    assertThat(response.title()).isEqualTo("Title");
  }

  @Test
  void updateClearsTheIndexFlagBecauseContentChanged() {
    Note existing = new Note();
    existing.setTitle("Old");
    existing.setContent("Old body");
    existing.setIndexedAt(java.time.Instant.now());
    UUID id = UUID.randomUUID();

    when(repository.findById(id)).thenReturn(Optional.of(existing));
    when(repository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = service.update(id, new NoteRequest("New", "New body", Set.of("x")));

    assertThat(response.title()).isEqualTo("New");
    assertThat(response.indexedAt()).isNull();
  }

  @Test
  void replaceTagsMutatesInPlaceSoHibernateCanTrackTheCollection() {
    Note note = new Note();
    var original = note.getTags();
    note.replaceTags(Set.of("a", "b"));

    assertThat(note.getTags()).isSameAs(original);
    assertThat(note.getTags()).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void missingNoteIsA404NotAnEmptyOptional() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(id.toString());
  }
}
