package dev.specra.api.feature.run.web;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.storage.FilesystemObjectStore;
import dev.specra.api.feature.run.dto.ArtifactLinkResponse;
import dev.specra.api.feature.run.service.ArtifactService;
import dev.specra.api.feature.run.service.RunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evidence: the links, and the download the links point at.
 *
 * <p>Two endpoints with two different authorisations on purpose. Listing links is an ordinary
 * authenticated call checked against the project. The download itself is reachable without a
 * header, because a browser fetching a video or opening a trace sends none — its authorisation is
 * the signature in the URL, and the signature is checked here before a byte is read.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Artifacts", description = "Traces, videos and screenshots, behind expiring links.")
public class ArtifactController {

  private final ArtifactService artifacts;
  private final RunService runs;
  private final FilesystemObjectStore signatures;

  public ArtifactController(
      ArtifactService artifacts, RunService runs, FilesystemObjectStore signatures) {
    this.artifacts = artifacts;
    this.runs = runs;
    this.signatures = signatures;
  }

  @GetMapping("/run-items/{id}/artifacts")
  @Operation(summary = "Signed, expiring links to one run item's evidence; never raw bytes")
  public List<ArtifactLinkResponse> links(@PathVariable UUID id) {
    return artifacts.links(runs.requireItem(id));
  }

  /**
   * The download.
   *
   * <p>Refuses an unsigned, altered or expired link with 403 rather than 404: the caller has a link
   * that is simply no longer valid, and telling them it does not exist would send them looking for
   * the wrong problem.
   */
  @GetMapping("/artifacts")
  @Operation(summary = "Fetch an artifact with a signed link. No Authorization header required.")
  public ResponseEntity<InputStreamResource> download(
      @RequestParam String key, @RequestParam long expires, @RequestParam("sig") String signature) {
    if (!signatures.isSignatureValid(key, expires, signature)) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "error.artifact.link-invalid");
    }

    InputStream stream = artifacts.open(key).orElseThrow(() -> artifacts.notFound(key));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        // Named so a saved trace is a file the user can open, not "download (3)".
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName(key) + "\"")
        .body(new InputStreamResource(stream));
  }

  private static String fileName(String key) {
    int slash = key.lastIndexOf('/');
    return slash < 0 ? key : key.substring(slash + 1);
  }
}
