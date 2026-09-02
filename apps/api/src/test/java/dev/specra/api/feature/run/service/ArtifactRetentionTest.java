package dev.specra.api.feature.run.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specra.api.core.storage.ObjectStore;
import dev.specra.api.feature.run.domain.TestArtifact;
import dev.specra.api.feature.run.domain.TestArtifactRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The sweep, and the ordering that keeps it safe to interrupt.
 *
 * <p>The bytes go before the row. A row with no file behind it is a broken link a user can click; a
 * file with no row is invisible waste. If the process dies between the two, the second is the one
 * to be left with.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactRetentionTest {

  @Mock TestArtifactRepository artifacts;
  @Mock ObjectStore store;

  private TestArtifact expired(String key) {
    TestArtifact artifact = new TestArtifact();
    artifact.setStorageKey(key);
    artifact.setExpiresAt(Instant.now().minusSeconds(60));
    return artifact;
  }

  @Test
  void removesTheBytesAndThenTheRow() {
    TestArtifact artifact = expired("runs/r1/i1/trace.zip");
    when(artifacts.findByExpiresAtBefore(any(), any())).thenReturn(List.of(artifact));

    int removed = new ArtifactRetention(artifacts, store).deleteExpired();

    assertThat(removed).isEqualTo(1);
    var order = org.mockito.Mockito.inOrder(store, artifacts);
    order.verify(store).delete("runs/r1/i1/trace.zip");
    order.verify(artifacts).delete(artifact);
  }

  /**
   * A file that cannot be removed keeps its row, so the next pass tries again. Deleting the row
   * anyway would orphan the bytes for ever — nothing would know they were there to remove.
   */
  @Test
  void leavesTheRowWhenTheBytesCouldNotBeRemoved() {
    TestArtifact artifact = expired("runs/r1/i1/trace.zip");
    when(artifacts.findByExpiresAtBefore(any(), any())).thenReturn(List.of(artifact));
    doThrow(new IllegalStateException("disk is busy")).when(store).delete(any());

    int removed = new ArtifactRetention(artifacts, store).deleteExpired();

    assertThat(removed).isZero();
    verify(artifacts, never()).delete(artifact);
  }

  /** One bad artifact must not stop the rest of the batch. */
  @Test
  void carriesOnPastOneFailure() {
    TestArtifact bad = expired("runs/r1/i1/bad.zip");
    TestArtifact good = expired("runs/r1/i2/good.zip");
    when(artifacts.findByExpiresAtBefore(any(), any())).thenReturn(List.of(bad, good));
    doThrow(new IllegalStateException("nope")).when(store).delete("runs/r1/i1/bad.zip");

    int removed = new ArtifactRetention(artifacts, store).deleteExpired();

    assertThat(removed).isEqualTo(1);
    verify(artifacts).delete(good);
  }

  @Test
  void doesNothingWhenNothingHasExpired() {
    when(artifacts.findByExpiresAtBefore(any(), any())).thenReturn(List.of());

    assertThat(new ArtifactRetention(artifacts, store).deleteExpired()).isZero();
    verify(store, never()).delete(any());
  }
}
