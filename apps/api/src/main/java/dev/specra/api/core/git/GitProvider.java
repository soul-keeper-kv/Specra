package dev.specra.api.core.git;

import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.Pageable;

/**
 * The port every Git host is reached through — the same shape as {@code ContentStore}: the
 * interface lives in {@code core}, an implementation lives in its feature, and callers hold the
 * port, never a host's client.
 *
 * <p>The abstraction earns its keep on <em>authentication, remotes and hosted features</em>, not on
 * {@code git commit} — which is the same everywhere and is why one JGit-backed implementation
 * covers the local mechanics for every host. No JGit type appears in these signatures on purpose:
 * the day an implementation shells out to a different library, this file does not move.
 *
 * <p>Implementations translate transport failures into the shared vocabulary — a rejected
 * credential is {@code GIT_AUTH_FAILED}, a non-fast-forward push is {@code GIT_PUSH_REJECTED} — so
 * the web layer renders one problem document regardless of host.
 */
public interface GitProvider {

  /** Stable lower-case discriminator, e.g. {@code "github"}; what the repository row names. */
  String kind();

  void cloneInto(RepoRef ref, String branch, Path into);

  void pull(RepoRef ref, Path copy);

  /** Asks the remote, not a copy — this is what "verify the connection" runs. */
  BranchList branches(RepoRef ref);

  void createBranch(Path copy, String name, String from);

  void checkout(Path copy, String branch);

  GitStatus status(Path copy);

  /** Unified diff of the working tree against HEAD; {@code path} null means everything. */
  String diff(Path copy, String path);

  CommitResult commit(Path copy, String message, List<String> paths, Author author);

  void push(RepoRef ref, Path copy, String branch);

  List<CommitInfo> history(Path copy, String path, Pageable page);

  /** How many commits {@link #history} pages over; small repos, honest totals. */
  long historySize(Path copy, String path);
}
