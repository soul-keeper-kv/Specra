package dev.specra.api.feature.run.service;

import dev.specra.api.config.SpecraProperties;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.storage.ObjectStore;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.run.domain.ArtifactKind;
import dev.specra.api.feature.run.domain.TestArtifact;
import dev.specra.api.feature.run.domain.TestRunItem;
import dev.specra.api.feature.run.dto.ArtifactLinkResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moving evidence off the runner's disk and handing it back as expiring links.
 *
 * <p>Two boundaries meet here. The runner produced files in a temp directory it is about to delete,
 * so they have to be copied out while the job's answer is still fresh. And a browser has to be able
 * to fetch them without the API streaming bytes through itself — a trace is megabytes and a request
 * thread is not a file server.
 */
@Service
@Transactional(readOnly = true)
public class ArtifactService {

  private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

  private final ObjectStore store;
  private final ProjectService projects;
  private final Duration urlTtl;
  private final Duration retention;

  public ArtifactService(ObjectStore store, ProjectService projects, SpecraProperties properties) {
    this.store = store;
    this.projects = projects;
    this.urlTtl = properties.storage().urlTtl();
    this.retention = properties.storage().retention();
  }

  /**
   * Copies one item's artifacts into the store and records them.
   *
   * <p>Best-effort per file: a trace that could not be copied must not cost the user the video
   * beside it, nor turn a run that produced real results into an error. What is missing is simply
   * absent from the run detail, and the reason is in the log.
   *
   * @param outputDir where the runner wrote them; paths in {@code reported} are relative to it
   */
  @Transactional
  public void store(TestRunItem item, String outputDir, List<Map<String, Object>> reported) {
    for (Map<String, Object> artifact : reported) {
      String relative = String.valueOf(artifact.get("path"));
      Path source = Path.of(outputDir).resolve(relative);
      if (!Files.isRegularFile(source)) {
        log.debug("Artifact {} is not on disk; skipping", source);
        continue;
      }

      ArtifactKind kind = kindOf(artifact.get("kind"));
      if (kind == null) {
        continue;
      }

      // runs/{runId}/{itemId}/… — the layout 06-execution.md fixes, so a run's evidence is one
      // prefix and deleting it is one sweep.
      String key =
          "runs/" + item.getRun().getId() + "/" + item.getId() + "/" + fileNameOf(relative);
      try {
        store.put(key, source, String.valueOf(artifact.getOrDefault("contentType", "")));

        TestArtifact row = new TestArtifact();
        row.setKind(kind);
        row.setStorageKey(key);
        row.setContentType(stringOrNull(artifact.get("contentType")));
        row.setSizeBytes(Files.size(source));
        row.setExpiresAt(Instant.now().plus(retention));
        item.addArtifact(row);
      } catch (IOException | RuntimeException e) {
        log.warn("Could not store artifact {} for item {}", relative, item.getId(), e);
      }
    }
  }

  /**
   * Short-lived links for one run item's evidence.
   *
   * <p>Minted on request rather than stored, so a link cannot outlive its own expiry by sitting in
   * a cached response — and so an expired one is re-issued by reloading the page rather than by
   * anyone having to think about it.
   */
  public List<ArtifactLinkResponse> links(TestRunItem item) {
    projects.requireAccess(item.getRun().getProjectId(), Permission.CONTENT_VIEW);

    List<ArtifactLinkResponse> links = new ArrayList<>();
    for (TestArtifact artifact : item.getArtifacts()) {
      store
          .url(artifact.getStorageKey(), urlTtl)
          .ifPresent(
              url ->
                  links.add(
                      new ArtifactLinkResponse(
                          artifact.getId(),
                          artifact.getKind(),
                          url,
                          artifact.getContentType(),
                          artifact.getSizeBytes(),
                          Instant.now().plus(urlTtl))));
    }
    return links;
  }

  /** Unknown kinds are dropped rather than guessed: a mislabelled artifact is worse than none. */
  private static ArtifactKind kindOf(Object value) {
    try {
      return ArtifactKind.valueOf(String.valueOf(value));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * The file's own name, not the runner's nested path.
   *
   * <p>The engine nests artifacts under a directory per test, and that structure means nothing once
   * the key already carries the run and the item. Flattening also keeps a path from the runner's
   * filesystem out of a key that ends up in a URL.
   */
  private static String fileNameOf(String relative) {
    int slash = relative.lastIndexOf('/');
    return slash < 0 ? relative : relative.substring(slash + 1);
  }

  private static String stringOrNull(Object value) {
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
  }

  /** Reads an artifact back for a signed download; the controller has already checked the link. */
  public java.util.Optional<java.io.InputStream> open(String key) {
    return store.open(key);
  }

  /** The one throw site for an artifact that is not in the store. */
  public ResourceNotFoundException notFound(String key) {
    return new ResourceNotFoundException("resource.artifact", key);
  }
}
