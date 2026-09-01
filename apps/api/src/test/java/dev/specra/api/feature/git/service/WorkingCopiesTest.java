package dev.specra.api.feature.git.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.support.TestProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkingCopiesTest {

  private final WorkingCopies copies =
      new WorkingCopies(TestProperties.withContentKind("testcase"));

  /**
   * One directory per project, named by the id — not per branch. Branch switching happens inside
   * it, which is why {@code checkout} is refused while the copy is dirty.
   */
  @Test
  void keepsOneDirectoryPerProject() {
    UUID project = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    assertThat(copies.pathFor(project).getFileName().toString()).isEqualTo(project.toString());
    assertThat(copies.pathFor(project)).isNotEqualTo(copies.pathFor(other));
    assertThat(copies.pathFor(project).getParent()).isEqualTo(copies.pathFor(other).getParent());
  }

  /** A project id cannot escape the root, which is what makes the directory name safe. */
  @Test
  void staysUnderTheConfiguredRoot() {
    UUID project = UUID.randomUUID();
    Path copy = copies.pathFor(project);

    assertThat(copy.normalize().toString()).isEqualTo(copy.toString());
    assertThat(copy.getParent().getFileName().toString()).isEqualTo("test-repos");
  }

  /** Two threads must not mutate one project's directory at once; the lock is what stops them. */
  @Test
  void serialisesWorkOnOneProject() throws Exception {
    UUID project = UUID.randomUUID();
    List<String> events = new ArrayList<>();
    CountDownLatch bothDone = new CountDownLatch(2);

    Runnable task =
        () ->
            copies.withProjectLock(
                project,
                () -> {
                  synchronized (events) {
                    events.add("enter");
                  }
                  try {
                    Thread.sleep(30);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  synchronized (events) {
                    events.add("exit");
                  }
                  bothDone.countDown();
                  return null;
                });

    Thread first = new Thread(task);
    Thread second = new Thread(task);
    first.start();
    second.start();
    assertThat(bothDone.await(5, TimeUnit.SECONDS)).isTrue();
    first.join();
    second.join();

    // Interleaved work would read enter, enter, exit, exit.
    assertThat(events).containsExactly("enter", "exit", "enter", "exit");
  }
}
