package dev.specra.api.feature.git.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.git.Author;
import dev.specra.api.core.git.BranchList;
import dev.specra.api.core.git.CommitInfo;
import dev.specra.api.core.git.CommitResult;
import dev.specra.api.core.git.FileChange;
import dev.specra.api.core.git.GitStatus;
import dev.specra.api.core.git.RepoRef;
import dev.specra.api.support.TestProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;

/**
 * The provider driven against a real repository — a bare one on disk, reached over {@code file://}.
 *
 * <p>No network and no GitHub account, and it still exercises the code a real remote does: clone,
 * status, diff, commit, push, branch, checkout and history all run through JGit's transport layer
 * exactly as they would over HTTPS. What a hosted remote adds is authentication and pull requests,
 * and neither is what these assertions are about.
 *
 * <p>This is the one test that may import JGit besides the provider itself — it has to build the
 * fixture the provider then talks to.
 */
class GithubGitProviderTest {

  private static final Author AUTHOR = new Author("Nguyen QA", "qa@specra.dev");

  private final GithubGitProvider provider =
      new GithubGitProvider(TestProperties.withContentKind("testcase"));

  @TempDir Path tmp;

  private Path bare;
  private RepoRef ref;

  @BeforeEach
  void seedRemote() throws Exception {
    bare = tmp.resolve("remote.git");
    Git.init().setDirectory(bare.toFile()).setBare(true).setInitialBranch("main").call().close();

    Path seed = tmp.resolve("seed");
    try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch("main").call()) {
      write(seed, "README.md", "# storefront-e2e\n");
      git.add().addFilepattern("README.md").call();
      git.commit()
          .setMessage("chore: initial commit")
          .setAuthor(new PersonIdent("Seed", "seed@specra.dev"))
          .setSign(false)
          .call();
      git.remoteAdd()
          .setName("origin")
          .setUri(new org.eclipse.jgit.transport.URIish(remoteUrl()))
          .call();
      git.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
          .call();
    }

    ref = new RepoRef(remoteUrl(), "main", null);
  }

  /**
   * JGit keeps pack files open in a process-wide window cache, and Windows refuses to delete an
   * open file — so without this, {@code @TempDir} cleanup fails the test that ran last rather than
   * the code under test. Reinstalling the cache closes the handles.
   */
  @AfterEach
  void releaseOpenPackFiles() {
    RepositoryCache.clear();
    new WindowCacheConfig().install();
  }

  @Test
  void clonesAndReportsACleanStatus() {
    Path copy = clone("main");

    GitStatus status = provider.status(copy);

    assertThat(status.branch()).isEqualTo("main");
    assertThat(status.clean()).isTrue();
    assertThat(status.ahead()).isZero();
    assertThat(status.behind()).isZero();
  }

  @Test
  void seesAnUntrackedFileAndDiffsAModifiedOne() throws Exception {
    Path copy = clone("main");
    write(copy, "tests/login.spec.ts", "test('login', async () => {});\n");
    write(copy, "README.md", "# storefront-e2e\n\nNow with tests.\n");

    GitStatus status = provider.status(copy);

    assertThat(status.clean()).isFalse();
    assertThat(status.changes())
        .contains(new FileChange("tests/login.spec.ts", FileChange.ChangeKind.UNTRACKED))
        .contains(new FileChange("README.md", FileChange.ChangeKind.MODIFIED));

    assertThat(provider.diff(copy, "README.md"))
        .contains("--- a/README.md")
        .contains("+Now with tests.");
  }

  /**
   * The whole point of taking paths: a commit carries what was picked, not what is lying around.
   */
  @Test
  void commitsOnlyTheGivenPathsAndAttributesThemToTheAuthor() throws Exception {
    Path copy = clone("main");
    write(copy, "wanted.txt", "in the commit\n");
    write(copy, "unwanted.txt", "not in the commit\n");

    CommitResult result =
        provider.commit(copy, "test: add the wanted file", List.of("wanted.txt"), AUTHOR);

    assertThat(result.sha()).isNotBlank();

    List<CommitInfo> history = provider.history(copy, null, PageRequest.of(0, 10));
    assertThat(history).hasSize(2);
    assertThat(history.get(0).message()).isEqualTo("test: add the wanted file");
    assertThat(history.get(0).authorName()).isEqualTo("Nguyen QA");
    assertThat(history.get(0).authorEmail()).isEqualTo("qa@specra.dev");

    // Still dirty: the file that was not named is untouched, not swept in.
    assertThat(provider.status(copy).changes())
        .containsExactly(new FileChange("unwanted.txt", FileChange.ChangeKind.UNTRACKED));
  }

  @Test
  void refusesACommitThatWouldContainNothing() throws Exception {
    Path copy = clone("main");
    write(copy, "untouched.txt", "x\n");
    provider.commit(copy, "test: first", List.of("untouched.txt"), AUTHOR);

    assertThatThrownBy(() -> provider.commit(copy, "test: again", List.of("untouched.txt"), AUTHOR))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("nothing-to-commit");
  }

  @Test
  void pushesToTheRemoteAndClearsTheAheadCount() throws Exception {
    Path copy = clone("main");
    write(copy, "pushed.txt", "published\n");
    provider.commit(copy, "test: publish a file", List.of("pushed.txt"), AUTHOR);

    assertThat(provider.status(copy).ahead()).isEqualTo(1);

    provider.push(ref, copy, "main");

    assertThat(provider.status(copy).ahead()).isZero();
    // A second clone sees it, which is what "the remote has it" means.
    assertThat(clone("main", "verify")).isDirectoryContaining(p -> p.endsWith("pushed.txt"));
  }

  /**
   * The invariant behind GIT_PUSH_REJECTED: someone else moved the branch, and the answer is a
   * reported conflict for the user to pull — never a force push.
   */
  @Test
  void reportsANonFastForwardPushRatherThanForcingIt() throws Exception {
    // Both clones happen first: that is what makes the two histories diverge rather than one
    // simply trailing the other, which is the case a fast-forward would quietly resolve.
    Path mine = clone("main", "mine");
    Path theirs = clone("main", "theirs");

    write(theirs, "theirs.txt", "first\n");
    provider.commit(theirs, "test: their change", List.of("theirs.txt"), AUTHOR);
    provider.push(ref, theirs, "main");

    write(mine, "mine.txt", "second\n");
    provider.commit(mine, "test: my change", List.of("mine.txt"), AUTHOR);

    assertThatThrownBy(() -> provider.push(ref, mine, "main"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).errorCode())
                    .isEqualTo(ErrorCode.GIT_PUSH_REJECTED));

    // And the remote still has their commit, not ours: nothing was forced over it.
    Path after = clone("main", "after");
    assertThat(after.resolve("theirs.txt")).exists();
    assertThat(after.resolve("mine.txt")).doesNotExist();
  }

  @Test
  void createsABranchChecksItOutAndListsWhatTheRemoteHas() throws Exception {
    Path copy = clone("main");

    provider.createBranch(copy, "specra/tc-104", "main");
    provider.checkout(copy, "specra/tc-104");
    assertThat(provider.status(copy).branch()).isEqualTo("specra/tc-104");

    write(copy, "spec.txt", "on the branch\n");
    provider.commit(copy, "test: work on the branch", List.of("spec.txt"), AUTHOR);
    provider.push(ref, copy, "specra/tc-104");

    BranchList branches = provider.branches(ref);
    assertThat(branches.names()).contains("main", "specra/tc-104");
    assertThat(branches.defaultBranch()).isEqualTo("main");

    provider.checkout(copy, "main");
    assertThat(provider.status(copy).branch()).isEqualTo("main");
  }

  @Test
  void pullsWhatSomebodyElsePushed() throws Exception {
    Path theirs = clone("main", "theirs");
    write(theirs, "from-them.txt", "hello\n");
    provider.commit(theirs, "test: their file", List.of("from-them.txt"), AUTHOR);
    provider.push(ref, theirs, "main");

    Path mine = clone("main", "mine");
    // "mine" was cloned after the push, so pull is a no-op that must still succeed cleanly.
    provider.pull(ref, mine);

    assertThat(mine.resolve("from-them.txt")).exists();
    assertThat(provider.status(mine).clean()).isTrue();
  }

  @Test
  void anUnknownBranchIsAnInvalidParameterRatherThanACrash() {
    Path copy = clone("main");

    assertThatThrownBy(() -> provider.checkout(copy, "no-such-branch"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e ->
                assertThat(((BusinessException) e).errorCode())
                    .isEqualTo(ErrorCode.INVALID_PARAMETER));
  }

  @Test
  void historyCanBeNarrowedToOnePathAndCounted() throws Exception {
    Path copy = clone("main");
    write(copy, "a.txt", "a\n");
    provider.commit(copy, "test: add a", List.of("a.txt"), AUTHOR);
    write(copy, "b.txt", "b\n");
    provider.commit(copy, "test: add b", List.of("b.txt"), AUTHOR);

    assertThat(provider.historySize(copy, null)).isEqualTo(3);
    assertThat(provider.historySize(copy, "a.txt")).isEqualTo(1);
    assertThat(provider.history(copy, "a.txt", PageRequest.of(0, 10)))
        .singleElement()
        .satisfies(c -> assertThat(c.message()).isEqualTo("test: add a"));

    // Paging is a skip plus a count, so page 1 of size 1 is the second-newest commit.
    assertThat(provider.history(copy, null, PageRequest.of(1, 1)))
        .singleElement()
        .satisfies(c -> assertThat(c.message()).isEqualTo("test: add a"));
  }

  private Path clone(String branch) {
    return clone(branch, "copy");
  }

  private Path clone(String branch, String name) {
    Path into = tmp.resolve(name + "-" + branch.replace('/', '_'));
    provider.cloneInto(ref, branch, into);
    return into;
  }

  private String remoteUrl() {
    return bare.toUri().toString();
  }

  private static void write(Path root, String relative, String content) throws IOException {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }
}
