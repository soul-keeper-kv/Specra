package dev.specra.api.feature.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.content.ContentCapability;
import dev.specra.api.core.content.ContentQuery;
import dev.specra.api.core.content.ContentStoreRegistry;
import dev.specra.api.core.content.UnsupportedContentOperationException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.support.FakeContentStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * These run the tools the way the model will: through the same objects, with the same sloppy
 * arguments a model produces.
 */
class ContentToolsTest {

  private final FakeContentStore store =
      FakeContentStore.full("note")
          .with("1", "Kickoff", "We agreed to ship the pgvector spike first.", "q3")
          .with("2", "Retro", "The deploy script needs owners.", "q3")
          .with("3", "Ideas", "Unrelated scratch notes.");

  private final ContentTools tools = new ContentTools(registry(store, "note"));

  @Test
  void springAiExposesBothToolsUnderTheirDeclaredNames() {
    ToolCallback[] callbacks = ToolCallbacks.from(tools);

    assertThat(callbacks)
        .extracting(callback -> callback.getToolDefinition().name())
        .containsExactlyInAnyOrder("search_content", "get_content");
  }

  @Test
  void searchReportsTheTotalSoTheModelDoesNotImplyItSawEverything() {
    var result = tools.searchContent(null, "q3", 1);

    assertThat(result.totalMatches()).isEqualTo(2);
    assertThat(result.results()).hasSize(1);
  }

  @Test
  void omittedArgumentsMeanNoFilterAndTheDefaultPageSize() {
    var result = tools.searchContent(null, null, null);

    assertThat(result.totalMatches()).isEqualTo(3);
    assertThat(result.results()).hasSize(3);
  }

  @Test
  void anAbsurdLimitIsClampedRatherThanHonoured() {
    var result = tools.searchContent("", "", 10_000);

    assertThat(result.results()).hasSize(3);
    assertThat(ContentQuery.MAX_SIZE).isEqualTo(50);
  }

  @Test
  void getReturnsTheWholeBody() {
    var document = tools.getContent("1");

    assertThat(document.body()).contains("pgvector spike");
    assertThat(document.kind()).isEqualTo("note");
  }

  @Test
  void anIdTheModelInventedIsANotFoundTheModelCanRecoverFrom() {
    assertThatThrownBy(() -> tools.getContent("99")).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aStoreThatCannotEvenBeReadRefusesBeforeDoingAnyWork() {
    var writeOnly = new FakeContentStore("outbox", ContentCapability.CREATE);
    var blocked = new ContentTools(registry(writeOnly, "outbox"));

    assertThatThrownBy(() -> blocked.searchContent("x", null, null))
        .isInstanceOf(UnsupportedContentOperationException.class);
  }

  private static ContentStoreRegistry registry(FakeContentStore store, String defaultKind) {
    return new ContentStoreRegistry(
        List.of(store),
        new SpecraProperties(
            new SpecraProperties.Cors(List.of("http://localhost:3000")),
            new SpecraProperties.Ai(
                "prompt", new SpecraProperties.Ai.ChatMemory(40), new SpecraProperties.Ai.Rag(800)),
            new SpecraProperties.Content(defaultKind),
            new SpecraProperties.Logging(new SpecraProperties.Logging.Access(true, 1000))));
  }
}
