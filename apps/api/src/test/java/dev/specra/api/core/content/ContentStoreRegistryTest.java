package dev.specra.api.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.support.FakeContentStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The registry is the seam the whole abstraction turns on, so the tests are mostly about its
 * failure modes: every one of them is a misconfiguration that must be loud at startup rather than
 * quiet until a user hits it.
 */
class ContentStoreRegistryTest {

  @Test
  void resolvesTheConfiguredDefaultWhenNoKindIsNamed() {
    var registry =
        new ContentStoreRegistry(
            List.of(FakeContentStore.full("note"), FakeContentStore.full("file")),
            properties("file"));

    assertThat(registry.defaultStore().kind()).isEqualTo("file");
    assertThat(registry.require(null).kind()).isEqualTo("file");
    assertThat(registry.require("  ").kind()).isEqualTo("file");
    assertThat(registry.kinds()).containsExactlyInAnyOrder("note", "file");
  }

  @Test
  void resolvesByKindIgnoringCaseAndSurroundingSpace() {
    var registry =
        new ContentStoreRegistry(List.of(FakeContentStore.full("note")), properties("note"));

    assertThat(registry.require(" NOTE ").kind()).isEqualTo("note");
  }

  @Test
  void unknownKindNamesTheValidOptionsSoACallerCanCorrectItself() {
    var registry =
        new ContentStoreRegistry(List.of(FakeContentStore.full("note")), properties("note"));

    assertThatThrownBy(() -> registry.require("dropbox"))
        .isInstanceOf(UnknownContentKindException.class)
        .hasMessageContaining("dropbox")
        .hasMessageContaining("note");
  }

  @Test
  void refusesAnOperationTheStoreDoesNotDeclare() {
    var readOnly = new FakeContentStore("archive", ContentCapability.READ);
    var registry = new ContentStoreRegistry(List.of(readOnly), properties("archive"));

    assertThat(registry.requireCapable("archive", ContentCapability.READ)).isSameAs(readOnly);
    assertThatThrownBy(() -> registry.requireCapable("archive", ContentCapability.DELETE))
        .isInstanceOf(UnsupportedContentOperationException.class)
        .hasMessageContaining("DELETE");
  }

  @Test
  void aDefaultKindNoStoreAnswersToFailsTheBoot() {
    assertThatThrownBy(
            () ->
                new ContentStoreRegistry(
                    List.of(FakeContentStore.full("note")), properties("notez")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notez");
  }

  @Test
  void twoStoresClaimingOneKindFailTheBootRatherThanSilentlyShadowing() {
    assertThatThrownBy(
            () ->
                new ContentStoreRegistry(
                    List.of(FakeContentStore.full("note"), FakeContentStore.full("note")),
                    properties("note")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("note");
  }

  private static SpecraProperties properties(String defaultKind) {
    return new SpecraProperties(
        new SpecraProperties.Cors(List.of("http://localhost:3000")),
        new SpecraProperties.Ai(
            "prompt", new SpecraProperties.Ai.ChatMemory(40), new SpecraProperties.Ai.Rag(800)),
        new SpecraProperties.Content(defaultKind),
        new SpecraProperties.Logging(new SpecraProperties.Logging.Access(true, 1000)));
  }
}
