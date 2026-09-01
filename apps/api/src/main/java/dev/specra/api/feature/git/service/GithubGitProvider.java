package dev.specra.api.feature.git.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.git.Author;
import dev.specra.api.core.git.BranchList;
import dev.specra.api.core.git.CommitInfo;
import dev.specra.api.core.git.CommitResult;
import dev.specra.api.core.git.FileChange;
import dev.specra.api.core.git.GitAuth;
import dev.specra.api.core.git.GitProvider;
import dev.specra.api.core.git.GitStatus;
import dev.specra.api.core.git.RepoRef;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * GitHub over smart HTTP, with JGit doing the local work.
 *
 * <p>This is the only class in the code base that imports JGit — the same containment rule the LLM
 * vendors and the execution engines live under. Everything above it speaks {@code core.git}.
 *
 * <p>"GitHub" here is the authentication shape (a token as the password of a basic-auth pair, which
 * GitHub accepts under any username) plus the hosted features that arrive later; the local
 * mechanics are identical for every host, which is why a {@code file://} remote in a test drives
 * exactly the code a real repository does.
 */
@Component
public class GithubGitProvider implements GitProvider {

  public static final String KIND = "github";

  private static final String ORIGIN = "origin";

  private final PersonIdent committer;

  public GithubGitProvider(SpecraProperties properties) {
    this.committer =
        new PersonIdent(properties.git().committerName(), properties.git().committerEmail());
  }

  @Override
  public String kind() {
    return KIND;
  }

  @Override
  public void cloneInto(RepoRef ref, String branch, Path into) {
    try (Git git =
        Git.cloneRepository()
            .setURI(ref.remoteUrl())
            .setDirectory(into.toFile())
            .setBranch(branch)
            .setCloneAllBranches(true)
            .setCredentialsProvider(credentials(ref))
            .call()) {
      ensureUpstream(git.getRepository(), branch);
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void pull(RepoRef ref, Path copy) {
    try (Git git = open(copy)) {
      ensureUpstream(git.getRepository(), git.getRepository().getBranch());
      PullResult result =
          git.pull().setRemote(ORIGIN).setCredentialsProvider(credentials(ref)).call();
      if (!result.isSuccessful()) {
        // The merge left conflict markers; status now lists CONFLICTING paths for the user.
        throw new ConflictException(ErrorCode.CONFLICT.detailKey());
      }
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public BranchList branches(RepoRef ref) {
    try {
      Map<String, Ref> refs =
          Git.lsRemoteRepository()
              .setRemote(ref.remoteUrl())
              .setHeads(true)
              .setCredentialsProvider(credentials(ref))
              .callAsMap();
      List<String> names = new ArrayList<>();
      for (String name : new TreeMap<>(refs).keySet()) {
        if (name.startsWith(Constants.R_HEADS)) {
          names.add(name.substring(Constants.R_HEADS.length()));
        }
      }
      return new BranchList(defaultBranchOf(ref, refs, names), List.copyOf(names));
    } catch (GitAPIException e) {
      throw translate(e);
    }
  }

  @Override
  public void createBranch(Path copy, String name, String from) {
    try (Git git = open(copy)) {
      Repository repo = git.getRepository();
      String startPoint;
      if (repo.findRef(Constants.R_HEADS + from) != null) {
        startPoint = from;
      } else if (repo.findRef(Constants.R_REMOTES + ORIGIN + "/" + from) != null) {
        startPoint = ORIGIN + "/" + from;
      } else {
        throw invalidBranch();
      }
      git.branchCreate()
          .setName(name)
          .setStartPoint(startPoint)
          .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.NOTRACK)
          .call();
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void checkout(Path copy, String branch) {
    try (Git git = open(copy)) {
      Repository repo = git.getRepository();
      CheckoutCommand checkout = git.checkout().setName(branch);
      if (repo.findRef(Constants.R_HEADS + branch) == null) {
        if (repo.findRef(Constants.R_REMOTES + ORIGIN + "/" + branch) == null) {
          throw invalidBranch();
        }
        checkout
            .setCreateBranch(true)
            .setStartPoint(ORIGIN + "/" + branch)
            .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
      }
      checkout.call();
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public GitStatus status(Path copy) {
    try (Git git = open(copy)) {
      Repository repo = git.getRepository();
      Status status = git.status().call();

      // A path can appear in more than one set (staged and then edited again); the map keeps
      // one row per path and lets the later, more specific kind win.
      Map<String, FileChange.ChangeKind> kinds = new TreeMap<>();
      status.getAdded().forEach(p -> kinds.put(p, FileChange.ChangeKind.ADDED));
      status.getChanged().forEach(p -> kinds.put(p, FileChange.ChangeKind.MODIFIED));
      status.getModified().forEach(p -> kinds.put(p, FileChange.ChangeKind.MODIFIED));
      status.getRemoved().forEach(p -> kinds.put(p, FileChange.ChangeKind.DELETED));
      status.getMissing().forEach(p -> kinds.put(p, FileChange.ChangeKind.DELETED));
      status.getUntracked().forEach(p -> kinds.put(p, FileChange.ChangeKind.UNTRACKED));
      status.getConflicting().forEach(p -> kinds.put(p, FileChange.ChangeKind.CONFLICTING));

      List<FileChange> changes =
          kinds.entrySet().stream().map(e -> new FileChange(e.getKey(), e.getValue())).toList();

      String branch = repo.getBranch();
      BranchTrackingStatus tracking = BranchTrackingStatus.of(repo, branch);
      int ahead = tracking == null ? 0 : tracking.getAheadCount();
      int behind = tracking == null ? 0 : tracking.getBehindCount();
      return new GitStatus(branch, ahead, behind, changes);
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public String diff(Path copy, String path) {
    try (Git git = open(copy);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Repository repo = git.getRepository();
      DiffCommand diff =
          git.diff().setOutputStream(out).setOldTree(headTree(repo)).setNewTree(workTree(repo));
      if (path != null && !path.isBlank()) {
        diff.setPathFilter(PathFilter.create(path));
      }
      diff.call();
      return out.toString(StandardCharsets.UTF_8);
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public CommitResult commit(Path copy, String message, List<String> paths, Author author) {
    try (Git git = open(copy)) {
      for (String path : paths) {
        if (Files.exists(copy.resolve(path))) {
          git.add().addFilepattern(path).call();
        } else {
          git.rm().addFilepattern(path).call();
        }
      }

      Status staged = git.status().call();
      if (staged.getAdded().isEmpty()
          && staged.getChanged().isEmpty()
          && staged.getRemoved().isEmpty()) {
        throw new ConflictException("error.git.nothing-to-commit");
      }

      RevCommit commit =
          git.commit()
              .setMessage(message)
              .setAuthor(new PersonIdent(author.name(), author.email()))
              .setCommitter(committer)
              .setSign(false)
              .call();
      return new CommitResult(commit.getName(), commit.getFullMessage());
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void push(RepoRef ref, Path copy, String branch) {
    try (Git git = open(copy)) {
      String head = Constants.R_HEADS + branch;
      Iterable<PushResult> results =
          git.push()
              .setRemote(ORIGIN)
              .setRefSpecs(new RefSpec(head + ":" + head))
              .setCredentialsProvider(credentials(ref))
              .call();
      for (PushResult result : results) {
        for (RemoteRefUpdate update : result.getRemoteUpdates()) {
          switch (update.getStatus()) {
            case OK, UP_TO_DATE -> {}
            case REJECTED_NONFASTFORWARD, REJECTED_REMOTE_CHANGED ->
                throw new BusinessException(
                    ErrorCode.GIT_PUSH_REJECTED, ErrorCode.GIT_PUSH_REJECTED.detailKey());
            default ->
                throw new IllegalStateException(
                    "push of "
                        + branch
                        + " failed: "
                        + update.getStatus()
                        + " "
                        + update.getMessage());
          }
        }
      }
      ensureUpstream(git.getRepository(), branch);
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public List<CommitInfo> history(Path copy, String path, Pageable page) {
    try (Git git = open(copy)) {
      LogCommand log = git.log().setSkip((int) page.getOffset()).setMaxCount(page.getPageSize());
      if (path != null && !path.isBlank()) {
        log.addPath(path);
      }
      List<CommitInfo> out = new ArrayList<>();
      for (RevCommit commit : log.call()) {
        out.add(
            new CommitInfo(
                commit.getName(),
                commit.getFullMessage().strip(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                Instant.ofEpochSecond(commit.getCommitTime())));
      }
      return out;
    } catch (NoHeadException e) {
      return List.of();
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public long historySize(Path copy, String path) {
    try (Git git = open(copy)) {
      LogCommand log = git.log();
      if (path != null && !path.isBlank()) {
        log.addPath(path);
      }
      long count = 0;
      for (RevCommit ignored : log.call()) {
        count++;
      }
      return count;
    } catch (NoHeadException e) {
      return 0;
    } catch (GitAPIException e) {
      throw translate(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Git open(Path copy) throws IOException {
    return Git.open(copy.toFile());
  }

  /** Null when the ref carries no auth — public repos and {@code file://} fixtures. */
  private static CredentialsProvider credentials(RepoRef ref) {
    GitAuth auth = ref.auth();
    if (auth == null) {
      return null;
    }
    String username =
        auth.username() == null || auth.username().isBlank() ? "x-access-token" : auth.username();
    return new UsernamePasswordCredentialsProvider(username, auth.token());
  }

  /**
   * {@code branch.<name>.remote/merge}, so ahead/behind and pull know which remote branch this one
   * follows. Clone sets it for the checked-out branch only; a branch first pushed from here has to
   * be told.
   */
  private static void ensureUpstream(Repository repo, String branch) throws IOException {
    if (branch == null) {
      return;
    }
    StoredConfig config = repo.getConfig();
    if (config.getString("branch", branch, "remote") == null) {
      config.setString("branch", branch, "remote", ORIGIN);
      config.setString("branch", branch, "merge", Constants.R_HEADS + branch);
      config.save();
    }
  }

  private static AbstractTreeIterator headTree(Repository repo) throws IOException {
    ObjectId head = repo.resolve(Constants.HEAD + "^{tree}");
    if (head == null) {
      return new EmptyTreeIterator();
    }
    try (ObjectReader reader = repo.newObjectReader()) {
      CanonicalTreeParser parser = new CanonicalTreeParser();
      parser.reset(reader, head);
      return parser;
    }
  }

  private static AbstractTreeIterator workTree(Repository repo) {
    return new FileTreeIterator(repo);
  }

  /**
   * The remote's default branch: the configured one when the remote has it, otherwise the first
   * branch HEAD points at, otherwise whatever is configured (the UI can still warn).
   */
  private static String defaultBranchOf(RepoRef ref, Map<String, Ref> refs, List<String> names) {
    if (names.contains(ref.defaultBranch())) {
      return ref.defaultBranch();
    }
    Ref head = refs.get(Constants.HEAD);
    if (head != null && head.getObjectId() != null) {
      for (String name : names) {
        Ref candidate = refs.get(Constants.R_HEADS + name);
        if (candidate != null && head.getObjectId().equals(candidate.getObjectId())) {
          return name;
        }
      }
    }
    return ref.defaultBranch();
  }

  private static BusinessException invalidBranch() {
    return new BusinessException(
        ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.detailKey());
  }

  /**
   * JGit reports "who are you" and "where is it" alike as a transport failure; the message is the
   * only place the distinction lives, so it is read here once rather than in every caller.
   */
  private static RuntimeException translate(GitAPIException e) {
    if (e instanceof RefNotFoundException) {
      return invalidBranch();
    }
    if (e instanceof TransportException) {
      String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
      if (message.contains("not authorized")
          || message.contains("authentication")
          || message.contains("credentials")
          || message.contains("401")
          || message.contains("403")) {
        return new BusinessException(
            ErrorCode.GIT_AUTH_FAILED, ErrorCode.GIT_AUTH_FAILED.detailKey());
      }
      if (message.contains("not found in upstream")) {
        return invalidBranch();
      }
    }
    return new IllegalStateException("git operation failed: " + e.getMessage(), e);
  }
}
