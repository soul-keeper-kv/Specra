package dev.specra.api.feature.git.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.git.Author;
import dev.specra.api.core.git.BranchList;
import dev.specra.api.core.git.CommitResult;
import dev.specra.api.core.git.GitProvider;
import dev.specra.api.core.git.GitStatus;
import dev.specra.api.core.git.RepoRef;
import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.core.security.CurrentUser;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.web.PageResponse;
import dev.specra.api.feature.git.domain.GitRepository;
import dev.specra.api.feature.git.domain.GitRepositoryRepository;
import dev.specra.api.feature.git.dto.BranchRequest;
import dev.specra.api.feature.git.dto.CheckoutRequest;
import dev.specra.api.feature.git.dto.CommitInfoResponse;
import dev.specra.api.feature.git.dto.CommitRequest;
import dev.specra.api.feature.git.dto.CommitResponse;
import dev.specra.api.feature.git.dto.FileContentResponse;
import dev.specra.api.feature.git.dto.FileWriteRequest;
import dev.specra.api.feature.git.dto.GitStatusResponse;
import dev.specra.api.feature.git.dto.RepositoryRequest;
import dev.specra.api.feature.git.dto.RepositoryResponse;
import dev.specra.api.feature.git.dto.VerifyResponse;
import dev.specra.api.feature.git.mapper.GitMapper;
import dev.specra.api.feature.project.service.ProjectService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Git for one project: the connection row, and the operations that run against its working copy.
 *
 * <p>Two things it deliberately never does. It does not store file bodies — every read goes to the
 * working copy, which is a cache of the repository, and the repository is the truth. And it does
 * not resolve a rejected push: that surfaces as {@code GIT_PUSH_REJECTED} for the user to pull and
 * retry, never as a force push or a rebase nobody asked for (07-git.md).
 *
 * <p>Every operation that touches disk runs under {@link WorkingCopies#withProjectLock}, because a
 * working copy is a directory and two concurrent requests mutating one corrupt it.
 */
@Service
@Transactional(readOnly = true)
public class GitService {

  private final GitRepositoryRepository repositories;
  private final GitCredentialService credentials;
  private final GitProviders providers;
  private final WorkingCopies workingCopies;
  private final ProjectService projects;
  private final GitMapper mapper;

  public GitService(
      GitRepositoryRepository repositories,
      GitCredentialService credentials,
      GitProviders providers,
      WorkingCopies workingCopies,
      ProjectService projects,
      GitMapper mapper) {
    this.repositories = repositories;
    this.credentials = credentials;
    this.providers = providers;
    this.workingCopies = workingCopies;
    this.projects = projects;
    this.mapper = mapper;
  }

  // ── the connection ────────────────────────────────────────────────────────

  public RepositoryResponse get(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return mapper.toRepository(require(projectId));
  }

  /**
   * Connect or re-point the project's one repository. Re-pointing evicts the working copies: they
   * are a cache of a remote that is no longer this project's.
   */
  @Transactional
  public RepositoryResponse connect(UUID projectId, RepositoryRequest request) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);
    UUID workspaceId = projects.workspaceOf(projectId);

    GitRepository repository =
        repositories
            .findByProjectId(projectId)
            .orElseGet(
                () -> {
                  GitRepository created = new GitRepository();
                  created.setWorkspaceId(workspaceId);
                  created.setProjectId(projectId);
                  return created;
                });

    boolean remoteChanged = !request.remoteUrl().trim().equals(repository.getRemoteUrl());
    repository.setProvider(request.provider());
    repository.setRemoteUrl(request.remoteUrl().trim());
    repository.setDefaultBranch(request.defaultBranch().trim());
    repository.setCredentialId(request.credentialId());
    if (remoteChanged) {
      repository.setActiveBranch(null);
    }

    GitRepository saved = repositories.save(repository);
    if (remoteChanged) {
      workingCopies.evict(projectId);
    }
    return mapper.toRepository(saved);
  }

  @Transactional
  public void disconnect(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);
    repositories.delete(require(projectId));
    workingCopies.evict(projectId);
  }

  /** Asks the remote itself, so a green answer means the credential really works. */
  public VerifyResponse verify(UUID projectId) {
    GitRepository repository = requireForRead(projectId);
    BranchList branches = provider(repository).branches(refFor(repository));
    return new VerifyResponse(branches.defaultBranch(), branches.names());
  }

  // ── the working copy ──────────────────────────────────────────────────────

  public GitStatusResponse status(UUID projectId) {
    GitRepository repository = requireForRead(projectId);
    GitStatus status = inCopy(repository, (provider, copy) -> provider.status(copy));
    return mapper.toStatus(status);
  }

  public String diff(UUID projectId, String path) {
    GitRepository repository = requireForRead(projectId);
    return inCopy(repository, (provider, copy) -> provider.diff(copy, path));
  }

  public List<String> branches(UUID projectId) {
    GitRepository repository = requireForRead(projectId);
    return provider(repository).branches(refFor(repository)).names();
  }

  public PageResponse<CommitInfoResponse> history(UUID projectId, String path, Pageable pageable) {
    GitRepository repository = requireForRead(projectId);
    return this.<PageResponse<CommitInfoResponse>>inCopy(
        repository,
        (provider, copy) -> {
          long total = provider.historySize(copy, path);
          List<CommitInfoResponse> rows =
              provider.history(copy, path, pageable).stream().map(mapper::toCommit).toList();
          return PageResponse.of(rows, pageable, total);
        });
  }

  public FileContentResponse readFile(UUID projectId, String path) {
    GitRepository repository = requireForRead(projectId);
    return inCopy(
        repository,
        (provider, copy) -> {
          Path file = resolve(copy, path);
          if (!Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("resource.file", path);
          }
          try {
            return new FileContentResponse(path, Files.readString(file, StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  @Transactional
  public FileContentResponse writeFile(UUID projectId, FileWriteRequest request) {
    GitRepository repository = requireForWrite(projectId);
    return inCopy(
        repository,
        (provider, copy) -> {
          Path file = resolve(copy, request.path());
          try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, request.content(), StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          return new FileContentResponse(request.path(), request.content());
        });
  }

  /** Returns the status afterwards, so the UI's ahead/behind counters settle in one round trip. */
  @Transactional
  public GitStatusResponse pull(UUID projectId) {
    GitRepository repository = requireForWrite(projectId);
    return inCopy(
        repository,
        (provider, copy) -> {
          provider.pull(refFor(repository), copy);
          return mapper.toStatus(provider.status(copy));
        });
  }

  /**
   * Commits as the signed-in user: attribution belongs to whoever approved the change, and the
   * committer is Specra. Exactly the paths the caller named — never everything that is dirty.
   */
  @Transactional
  public CommitResponse commit(UUID projectId, CommitRequest request) {
    GitRepository repository = requireForWrite(projectId);
    Author author = currentAuthor();
    return inCopy(
        repository,
        (provider, copy) -> {
          CommitResult result =
              provider.commit(copy, request.message().trim(), request.paths(), author);
          return new CommitResponse(result.sha(), result.message());
        });
  }

  @Transactional
  public GitStatusResponse push(UUID projectId) {
    GitRepository repository = requireForWrite(projectId);
    return inCopy(
        repository,
        (provider, copy) -> {
          provider.push(refFor(repository), copy, repository.effectiveBranch());
          return mapper.toStatus(provider.status(copy));
        });
  }

  @Transactional
  public GitStatusResponse createBranch(UUID projectId, BranchRequest request) {
    GitRepository repository = requireForWrite(projectId);
    String from =
        StringUtils.hasText(request.from()) ? request.from().trim() : repository.getDefaultBranch();
    return this.<GitStatusResponse>inCopy(
        repository,
        (provider, copy) -> {
          requireClean(provider.status(copy));
          provider.createBranch(copy, request.name().trim(), from);
          provider.checkout(copy, request.name().trim());
          repository.setActiveBranch(request.name().trim());
          repositories.save(repository);
          return mapper.toStatus(provider.status(copy));
        });
  }

  /**
   * Moves the branch this project's operations act on. Refused while the copy is dirty: a checkout
   * that carried someone's uncommitted edits onto another branch is a surprise, not a feature.
   */
  @Transactional
  public GitStatusResponse checkout(UUID projectId, CheckoutRequest request) {
    GitRepository repository = requireForWrite(projectId);
    String branch = request.branch().trim();
    return this.<GitStatusResponse>inCopy(
        repository,
        (provider, copy) -> {
          requireClean(provider.status(copy));
          provider.checkout(copy, branch);
          repository.setActiveBranch(branch);
          repositories.save(repository);
          return mapper.toStatus(provider.status(copy));
        });
  }

  // ── plumbing ──────────────────────────────────────────────────────────────

  /** What a caller runs against the project's checked-out copy, holding the project's lock. */
  @FunctionalInterface
  private interface CopyWork<T> {
    T run(GitProvider provider, Path copy);
  }

  /**
   * Runs {@code work} against the project's copy, cloned if this is the first call and moved onto
   * the branch the connection says is active. The branch is synced here rather than remembered,
   * because the copy is a cache that anything may have deleted since the last request.
   */
  private <T> T inCopy(GitRepository repository, CopyWork<T> work) {
    return workingCopies.withProjectLock(
        repository.getProjectId(),
        () -> {
          GitProvider provider = provider(repository);
          Path copy = workingCopies.ensure(provider, refFor(repository), repository.getProjectId());
          String wanted = repository.effectiveBranch();
          if (!wanted.equals(provider.status(copy).branch())) {
            provider.checkout(copy, wanted);
          }
          return work.run(provider, copy);
        });
  }

  private GitRepository require(UUID projectId) {
    return repositories
        .findByProjectId(projectId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.REPOSITORY_NOT_CONNECTED,
                    ErrorCode.REPOSITORY_NOT_CONNECTED.detailKey()));
  }

  private GitRepository requireForRead(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return require(projectId);
  }

  private GitRepository requireForWrite(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);
    return require(projectId);
  }

  private GitProvider provider(GitRepository repository) {
    return providers.require(repository.getProvider());
  }

  /** Decrypts the credential for this one call; nothing keeps the plaintext afterwards. */
  private RepoRef refFor(GitRepository repository) {
    return new RepoRef(
        repository.getRemoteUrl(),
        repository.getDefaultBranch(),
        repository.getCredentialId() == null
            ? null
            : credentials.authFor(repository.getWorkspaceId(), repository.getCredentialId()));
  }

  private static void requireClean(GitStatus status) {
    if (!status.clean()) {
      throw new BusinessException(
          ErrorCode.WORKING_COPY_DIRTY, ErrorCode.WORKING_COPY_DIRTY.detailKey());
    }
  }

  /**
   * A path is resolved inside the working copy and checked to still be inside it — {@code
   * ../../etc/passwd} is a path traversal, and the check is one line here rather than trust spread
   * over every caller.
   */
  private static Path resolve(Path copy, String path) {
    Path resolved = copy.resolve(path).normalize();
    if (!resolved.startsWith(copy)) {
      throw new BusinessException(
          ErrorCode.INVALID_PARAMETER, ErrorCode.INVALID_PARAMETER.detailKey());
    }
    return resolved;
  }

  private static Author currentAuthor() {
    AuthenticatedUser user = CurrentUser.require();
    return new Author(user.displayName(), user.email());
  }
}
