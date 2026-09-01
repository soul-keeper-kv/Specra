package dev.specra.api.feature.git.web;

import dev.specra.api.core.web.PageResponse;
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
import dev.specra.api.feature.git.service.GitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Git for one project (05-api-contracts.md).
 *
 * <p>Everything here acts on the project's working copy, which the service clones on demand — no
 * endpoint takes or returns a path on the API's disk, because that path is a cache detail the
 * client must never learn to depend on.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@Tag(
    name = "Git",
    description = "The repository is the source of truth; this is how Specra reaches it.")
public class GitController {

  private final GitService service;

  public GitController(GitService service) {
    this.service = service;
  }

  @GetMapping("/repository")
  @Operation(summary = "The repository this project is connected to")
  public RepositoryResponse repository(@PathVariable UUID projectId) {
    return service.get(projectId);
  }

  @PutMapping("/repository")
  @Operation(summary = "Connect or re-point the repository; re-pointing drops the working copies")
  public RepositoryResponse connect(
      @PathVariable UUID projectId, @Valid @RequestBody RepositoryRequest request) {
    return service.connect(projectId, request);
  }

  @DeleteMapping("/repository")
  @Operation(summary = "Disconnect the repository; the remote itself is untouched")
  public ResponseEntity<Void> disconnect(@PathVariable UUID projectId) {
    service.disconnect(projectId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/repository/verify")
  @Operation(summary = "Ask the remote for its branches — proof the credential really works")
  public VerifyResponse verify(@PathVariable UUID projectId) {
    return service.verify(projectId);
  }

  @GetMapping("/git/status")
  @Operation(summary = "Branch, ahead/behind, and the changed files in the working copy")
  public GitStatusResponse status(@PathVariable UUID projectId) {
    return service.status(projectId);
  }

  /**
   * Text, not JSON: a unified diff is a format of its own and the client renders it as one.
   * Wrapping it in a JSON string would only make the client unwrap it again.
   */
  @GetMapping(value = "/git/diff", produces = MediaType.TEXT_PLAIN_VALUE)
  @Operation(summary = "Unified diff of the working copy against HEAD")
  public String diff(
      @PathVariable UUID projectId,
      @Parameter(description = "Limit the diff to one path") @RequestParam(required = false)
          String path) {
    return service.diff(projectId, path);
  }

  @GetMapping("/git/branches")
  @Operation(summary = "The branches the remote advertises")
  public List<String> branches(@PathVariable UUID projectId) {
    return service.branches(projectId);
  }

  @PostMapping("/git/branches")
  @Operation(summary = "Create a branch and switch to it; refused while the copy is dirty")
  public GitStatusResponse createBranch(
      @PathVariable UUID projectId, @Valid @RequestBody BranchRequest request) {
    return service.createBranch(projectId, request);
  }

  @PostMapping("/git/checkout")
  @Operation(summary = "Switch the branch this project acts on; refused while the copy is dirty")
  public GitStatusResponse checkout(
      @PathVariable UUID projectId, @Valid @RequestBody CheckoutRequest request) {
    return service.checkout(projectId, request);
  }

  @GetMapping("/git/history")
  @Operation(summary = "Commits on the current branch, newest first")
  public PageResponse<CommitInfoResponse> history(
      @PathVariable UUID projectId,
      @Parameter(description = "Only commits touching this path") @RequestParam(required = false)
          String path,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.history(projectId, path, pageable);
  }

  @PostMapping("/git/pull")
  @Operation(summary = "Fetch and merge the remote branch, then report the new status")
  public GitStatusResponse pull(@PathVariable UUID projectId) {
    return service.pull(projectId);
  }

  @PostMapping("/git/commit")
  @Operation(summary = "Commit exactly the paths given, authored by the signed-in user")
  public CommitResponse commit(
      @PathVariable UUID projectId, @Valid @RequestBody CommitRequest request) {
    return service.commit(projectId, request);
  }

  @PostMapping("/git/push")
  @Operation(summary = "Push the current branch; a non-fast-forward is reported, never forced")
  public GitStatusResponse push(@PathVariable UUID projectId) {
    return service.push(projectId);
  }

  @GetMapping("/git/file")
  @Operation(summary = "Read one text file out of the working copy")
  public FileContentResponse readFile(@PathVariable UUID projectId, @RequestParam String path) {
    return service.readFile(projectId, path);
  }

  @PutMapping("/git/file")
  @Operation(summary = "Write one text file into the working copy; commit it separately")
  public FileContentResponse writeFile(
      @PathVariable UUID projectId, @Valid @RequestBody FileWriteRequest request) {
    return service.writeFile(projectId, request);
  }
}
