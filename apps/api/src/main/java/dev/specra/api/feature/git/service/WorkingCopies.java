package dev.specra.api.feature.git.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.git.GitProvider;
import dev.specra.api.core.git.RepoRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The working copies on disk — a cache by contract (07-git.md): deletable at any time, re-cloned on
 * demand, never pointed into by the database.
 *
 * <p><b>One directory per project, not per branch.</b> Per-branch directories were the plan, and
 * they buy exactly one thing: uncommitted work surviving a branch switch. Since switching branches
 * is <em>refused</em> while the copy is dirty, there is never uncommitted work to preserve — while
 * the cost is real: a branch created locally does not exist on the remote yet, so the next
 * operation would look in a directory that has to be cloned from a branch nobody has pushed. One
 * directory, and {@code checkout} moves within it, the way a person's own clone works.
 *
 * <p>Also the concurrency boundary. Git operations mutate a directory, and two requests mutating
 * one project's directory at once corrupt it, so every operation runs under that project's lock.
 */
@Component
public class WorkingCopies {

  private final Path root;
  private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

  public WorkingCopies(SpecraProperties properties) {
    this.root = Path.of(properties.git().reposDir()).toAbsolutePath().normalize();
  }

  public <T> T withProjectLock(UUID projectId, Supplier<T> work) {
    ReentrantLock lock = locks.computeIfAbsent(projectId, id -> new ReentrantLock());
    lock.lock();
    try {
      return work.get();
    } finally {
      lock.unlock();
    }
  }

  /**
   * The project's copy, cloned at the repository's default branch if it is not there yet. The
   * caller moves it to the branch it wants. Call under {@link #withProjectLock}.
   */
  public Path ensure(GitProvider provider, RepoRef ref, UUID projectId) {
    Path copy = pathFor(projectId);
    if (Files.isDirectory(copy.resolve(".git"))) {
      return copy;
    }
    // A directory with no .git in it is a half-finished clone from an attempt that failed;
    // starting again beats resuming something whose state nobody knows.
    deleteRecursively(copy);
    try {
      Files.createDirectories(copy);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create working copy directory " + copy, e);
    }
    provider.cloneInto(ref, ref.defaultBranch(), copy);
    return copy;
  }

  /** Drops the copy — what re-pointing the remote does to a cache of somewhere else. */
  public void evict(UUID projectId) {
    deleteRecursively(pathFor(projectId));
  }

  /** The project id is already filesystem-safe, so it is the directory name. */
  Path pathFor(UUID projectId) {
    return root.resolve(projectId.toString());
  }

  private static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  // Windows keeps freshly-released pack files locked for a beat; a leftover
                  // file in a cache directory is untidy, not incorrect.
                  p.toFile().deleteOnExit();
                }
              });
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot delete working copy " + path, e);
    }
  }
}
