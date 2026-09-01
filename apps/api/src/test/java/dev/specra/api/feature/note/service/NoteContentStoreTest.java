package dev.specra.api.feature.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentDraft;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.note.dto.NoteRequest;
import dev.specra.api.feature.note.dto.NoteResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteContentStoreTest {

  @Mock NoteService notes;
  @Mock NoteIndexService index;

  @InjectMocks NoteContentStore store;

  @Test
  void declaresEveryCapabilityBecauseNotesAreFullyWritable() {
    assertThat(store.capabilities()).containsExactlyInAnyOrder(ContentCapability.values());
    assertThat(store.kind()).isEqualTo("note");
  }

  @Test
  void searchReturnsExcerptsRatherThanWholeBodies() {
    NoteResponse note = note(UUID.randomUUID(), "Kickoff", "x".repeat(1000));
    when(notes.search(eq("kick"), eq("q3"), any()))
        .thenReturn(new PageResponse<>(List.of(note), 0, 10, 84, 9, true, false));

    var page = store.search(ContentQuery.of("kick", "Q3", 10));

    assertThat(page.totalElements()).isEqualTo(84);
    assertThat(page.content())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.kind()).isEqualTo("note");
              assertThat(summary.title()).isEqualTo("Kickoff");
              assertThat(summary.excerpt()).hasSizeLessThan(1000).endsWith("…");
            });
  }

  @Test
  void anIdThisStoreCouldNeverHaveIssuedIsA404NotA500() {
    assertThatThrownBy(() -> store.get("../../etc/passwd"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void creatingReindexesSoRagCannotMissTheNewDocument() {
    UUID id = UUID.randomUUID();
    NoteResponse created = note(id, "T", "Body");
    when(notes.create(any(NoteRequest.class))).thenReturn(created);
    when(index.index(id)).thenReturn(created);

    var document = store.create(new ContentDraft("T", "Body", Set.of("a")));

    ArgumentCaptor<NoteRequest> request = ArgumentCaptor.forClass(NoteRequest.class);
    verify(notes).create(request.capture());
    assertThat(request.getValue().title()).isEqualTo("T");
    assertThat(request.getValue().tags()).containsExactly("a");
    verify(index).index(id);
    assertThat(document.id()).isEqualTo(id.toString());
  }

  @Test
  void deletingLeavesTheEmbeddingsToTheNoteDeletedEvent() {
    UUID id = UUID.randomUUID();

    store.delete(id.toString());

    verify(notes).delete(id);
    // Dropping them here as well would strand the chunks on a delete that then fails, and would
    // duplicate work NoteIndexService already does after the transaction commits.
    verifyNoInteractions(index);
  }

  private static NoteResponse note(UUID id, String title, String content) {
    return new NoteResponse(id, title, content, Set.of("q3"), null, Instant.EPOCH, Instant.EPOCH);
  }
}
