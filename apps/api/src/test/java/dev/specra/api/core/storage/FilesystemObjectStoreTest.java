package dev.specra.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specra.api.support.TestProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store, and the property that makes its links safe to hand out.
 *
 * <p>"Signed URL" is only meaningful if an unsigned or edited one is refused. These assert that
 * directly, because the alternative — a link anybody can guess — would let one tenant read
 * another's test evidence by typing a path.
 */
class FilesystemObjectStoreTest {

  @TempDir Path root;

  private FilesystemObjectStore store() {
    return new FilesystemObjectStore(TestProperties.withStorageDir(root.toString()));
  }

  private Path fileContaining(String text) throws IOException {
    Path file = root.resolve("source.txt");
    Files.writeString(file, text);
    return file;
  }

  @Test
  void storesAndReadsBackAnArtifact() throws IOException {
    FilesystemObjectStore store = store();
    store.put("runs/r1/i1/trace.zip", fileContaining("evidence"), "application/zip");

    Optional<InputStream> read = store.open("runs/r1/i1/trace.zip");

    assertThat(read).isPresent();
    try (InputStream stream = read.orElseThrow()) {
      assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("evidence");
    }
  }

  @Test
  void aMissingArtifactIsEmptyRatherThanAnError() {
    assertThat(store().open("runs/nope/i1/trace.zip")).isEmpty();
  }

  @Test
  void aFreshlyMintedLinkVerifies() {
    FilesystemObjectStore store = store();
    String url = store.url("runs/r1/i1/trace.zip", Duration.ofMinutes(15)).orElseThrow();

    long expires = Long.parseLong(paramOf(url, "expires"));
    String signature = paramOf(url, "sig");

    assertThat(store.isSignatureValid("runs/r1/i1/trace.zip", expires, signature)).isTrue();
  }

  /**
   * The key is a query parameter, never a path segment.
   *
   * <p>A storage key contains slashes, and Tomcat rejects an encoded slash inside a path with a 400
   * before any handler runs — which made every artifact link undownloadable, valid or not. Caught
   * by requesting one from a running server; this pins the shape so it cannot come back.
   */
  @Test
  void putsTheKeyInTheQueryStringSoASlashSurvives() {
    String url = store().url("runs/r1/i1/trace.zip", Duration.ofMinutes(15)).orElseThrow();

    assertThat(url).startsWith("/api/v1/artifacts?");
    assertThat(url.substring(0, url.indexOf('?'))).doesNotContain("%2F");
    assertThat(paramOf(url, "key")).isEqualTo("runs%2Fr1%2Fi1%2Ftrace.zip");
  }

  /** The point of signing: the key cannot be swapped for somebody else's. */
  @Test
  void aSignatureDoesNotTransferToAnotherKey() {
    FilesystemObjectStore store = store();
    String url = store.url("runs/mine/i1/trace.zip", Duration.ofMinutes(15)).orElseThrow();

    long expires = Long.parseLong(paramOf(url, "expires"));
    String signature = paramOf(url, "sig");

    assertThat(store.isSignatureValid("runs/theirs/i1/trace.zip", expires, signature)).isFalse();
  }

  /** Nor can the deadline be pushed out by editing the query string. */
  @Test
  void anExtendedExpiryInvalidatesTheSignature() {
    FilesystemObjectStore store = store();
    String url = store.url("runs/r1/i1/trace.zip", Duration.ofMinutes(15)).orElseThrow();

    long expires = Long.parseLong(paramOf(url, "expires"));
    String signature = paramOf(url, "sig");

    assertThat(store.isSignatureValid("runs/r1/i1/trace.zip", expires + 3600, signature)).isFalse();
  }

  @Test
  void anExpiredLinkIsRefusedEvenWithItsOwnSignature() {
    FilesystemObjectStore store = store();
    // A link that was valid for one second, a minute ago.
    String url = store.url("runs/r1/i1/trace.zip", Duration.ofSeconds(-60)).orElseThrow();

    long expires = Long.parseLong(paramOf(url, "expires"));
    String signature = paramOf(url, "sig");

    assertThat(store.isSignatureValid("runs/r1/i1/trace.zip", expires, signature)).isFalse();
  }

  @Test
  void aForgedSignatureIsRefused() {
    assertThat(
            store()
                .isSignatureValid(
                    "runs/r1/i1/trace.zip", System.currentTimeMillis() / 1000 + 600, "not-a-sig"))
        .isFalse();
  }

  /**
   * Keys are built by this application, but they arrive back through a URL — so a check that only
   * holds while nobody edits the request is not a check.
   */
  @Test
  void aKeyThatClimbsOutOfTheRootIsRefused() {
    assertThatThrownBy(() -> store().open("../../etc/passwd"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storage root");
  }

  private static String paramOf(String url, String name) {
    for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts[0].equals(name)) {
        return parts[1];
      }
    }
    throw new IllegalStateException(name + " is not in " + url);
  }
}
