package dev.specra.api.feature.environment.service;

import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.environment.domain.Environment;
import dev.specra.api.feature.environment.domain.EnvironmentRepository;
import dev.specra.api.feature.environment.domain.EnvironmentVariable;
import dev.specra.api.feature.environment.dto.EnvironmentRequest;
import dev.specra.api.feature.environment.dto.EnvironmentResponse;
import dev.specra.api.feature.environment.dto.EnvironmentVariableRequest;
import dev.specra.api.feature.environment.dto.EnvironmentVariableResponse;
import dev.specra.api.feature.project.service.ProjectService;
import dev.specra.api.feature.testcase.service.TestCaseService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Environments, and the one place a secret is ever decrypted.
 *
 * <p>Three rules do the work. A secret is encrypted before it is stored and never travels back out
 * through a response — reads say "set", not the value. A request that omits a secret's value keeps
 * the stored one, so re-saving the form does not wipe every credential. And when the server has no
 * encryption key, storing a secret is refused with the variable to set, because the alternative is
 * plaintext at rest, which invariant 6 forbids.
 *
 * <p>{@link #resolveForDispatch} is the deliberate exception: it returns plaintext. It is public
 * because the run feature calls it, and it is safe to be public for a structural reason rather than
 * a hopeful one — {@code ArchitectureTest} forbids the web layer from reaching a service of another
 * feature, so the only callers it can have are services, and its return type is a plain {@code Map}
 * that no DTO can accidentally serialise.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

  private final EnvironmentRepository repository;
  private final ProjectService projects;
  private final SecretsCipher cipher;
  private final TestCaseService testCases;

  public EnvironmentService(
      EnvironmentRepository repository,
      ProjectService projects,
      SecretsCipher cipher,
      TestCaseService testCases) {
    this.repository = repository;
    this.projects = projects;
    this.cipher = cipher;
    this.testCases = testCases;
  }

  public List<EnvironmentResponse> list(UUID projectId) {
    projects.requireAccess(projectId, Permission.CONTENT_VIEW);
    return repository.findByProjectIdOrderByNameAsc(projectId).stream()
        .map(EnvironmentService::toResponse)
        .toList();
  }

  public EnvironmentResponse get(UUID id) {
    Environment environment = require(id);
    projects.requireAccess(environment.getProjectId(), Permission.CONTENT_VIEW);
    return toResponse(environment);
  }

  @Transactional
  public EnvironmentResponse create(UUID projectId, EnvironmentRequest request) {
    projects.requireAccess(projectId, Permission.CONTENT_EDIT);

    String name = request.name().trim();
    if (repository.existsByProjectIdAndName(projectId, name)) {
      throw new ConflictException("error.environment.duplicate-name", name);
    }

    Environment environment = new Environment();
    environment.setWorkspaceId(projects.workspaceOf(projectId));
    environment.setProjectId(projectId);
    environment.setName(name);
    environment.setBaseUrl(request.baseUrl().trim());

    // The first environment of a project is the default whatever the request says: a project
    // with environments but no default would make every run ask a question with one answer.
    boolean first = repository.findByProjectIdOrderByNameAsc(projectId).isEmpty();
    applyDefault(environment, first || Boolean.TRUE.equals(request.isDefault()));
    environment.setPreludeTestCaseId(preludeOf(request, projectId));
    environment.replaceVariables(variablesOf(request, environment));

    return toResponse(repository.save(environment));
  }

  @Transactional
  public EnvironmentResponse update(UUID id, EnvironmentRequest request) {
    Environment environment = require(id);
    projects.requireAccess(environment.getProjectId(), Permission.CONTENT_EDIT);

    String name = request.name().trim();
    if (!name.equals(environment.getName())
        && repository.existsByProjectIdAndName(environment.getProjectId(), name)) {
      throw new ConflictException("error.environment.duplicate-name", name);
    }

    environment.setName(name);
    environment.setBaseUrl(request.baseUrl().trim());
    applyDefault(environment, Boolean.TRUE.equals(request.isDefault()));
    environment.setPreludeTestCaseId(preludeOf(request, environment.getProjectId()));
    environment.replaceVariables(variablesOf(request, environment));

    return toResponse(repository.save(environment));
  }

  @Transactional
  public void delete(UUID id) {
    Environment environment = require(id);
    projects.requireAccess(environment.getProjectId(), Permission.CONTENT_EDIT);
    repository.delete(environment);
  }

  /**
   * The environment a run should use when the request names none.
   *
   * <p>Public because the run feature asks for it; it returns the id rather than the entity, so the
   * aggregate — and its secrets — stays on this side of the boundary.
   */
  public UUID defaultEnvironmentId(UUID projectId) {
    return repository
        .findByProjectIdAndIsDefaultTrue(projectId)
        .map(Environment::getId)
        .orElseThrow(() -> new ResourceNotFoundException("resource.environment", projectId));
  }

  /**
   * Every variable of one environment, decrypted, for injection into a runner job.
   *
   * <p>The only method that returns a secret in the clear. It exists because generated code reads
   * configuration from its process environment and something has to put it there; the values live
   * in memory for the length of the dispatch, are never written to the working copy, and are masked
   * in whatever the run captures.
   */
  public Map<String, String> resolveForDispatch(UUID environmentId) {
    Environment environment = require(environmentId);
    Map<String, String> resolved = new LinkedHashMap<>();
    for (EnvironmentVariable variable : environment.getVariables()) {
      resolved.put(
          variable.getKey(),
          variable.isSecret() ? cipher.decrypt(variable.getValue()) : variable.getValue());
    }
    return resolved;
  }

  /**
   * The test case an inspection of this environment should replay first, or null.
   *
   * <p>Public for the same structural reason {@link #resolveForDispatch} is: only another service
   * can reach it, and it hands back an id rather than the aggregate.
   */
  public UUID preludeTestCaseIdOf(UUID environmentId) {
    return require(environmentId).getPreludeTestCaseId();
  }

  /** The base URL a run points at, for the job payload. */
  public String baseUrlOf(UUID environmentId) {
    return require(environmentId).getBaseUrl();
  }

  /** Loaded by id and then access-checked through the project, never the other way round. */
  public EnvironmentResponse requireAccessible(UUID environmentId, UUID projectId) {
    Environment environment = require(environmentId);
    if (!environment.getProjectId().equals(projectId)) {
      // Wrong project is "not found" rather than "forbidden": on a multi-tenant API, confirming
      // that a row exists somewhere else is itself the leak.
      throw new ResourceNotFoundException("resource.environment", environmentId);
    }
    return toResponse(environment);
  }

  private Environment require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("resource.environment", id));
  }

  /**
   * The prelude, checked to belong to this project.
   *
   * <p>Through the test case service rather than by reading the table: another feature owns that
   * row, and the check has to be the one that feature makes. A case from another project is
   * reported as not found for the reason {@link #requireAccessible} gives — confirming a row exists
   * somewhere else is itself the leak.
   */
  private UUID preludeOf(EnvironmentRequest request, UUID projectId) {
    UUID id = request.preludeTestCaseId();
    if (id == null) {
      return null;
    }
    if (!testCases.get(id).projectId().equals(projectId)) {
      throw new ResourceNotFoundException("resource.test-case", id);
    }
    return id;
  }

  /** At most one default per project, so setting one clears whichever held it. */
  private void applyDefault(Environment environment, boolean wanted) {
    if (!wanted) {
      environment.setDefault(false);
      return;
    }
    repository
        .findByProjectIdAndIsDefaultTrue(environment.getProjectId())
        .filter(previous -> !previous.getId().equals(environment.getId()))
        .ifPresent(
            previous -> {
              previous.setDefault(false);
              repository.save(previous);
            });
    environment.setDefault(true);
  }

  /**
   * Turns the requested variables into rows, carrying forward a secret whose value was omitted.
   *
   * <p>That carry-forward is what makes the edit form usable: a response cannot contain a secret,
   * so a client that round-trips one has nothing to send back, and treating "absent" as "clear it"
   * would delete every credential the first time somebody renamed the environment.
   */
  private List<EnvironmentVariable> variablesOf(EnvironmentRequest request, Environment existing) {
    List<EnvironmentVariable> rows = new ArrayList<>();
    if (request.variables() == null) {
      return rows;
    }

    // Snapshot the stored ciphertexts before anything replaces the collection. The caller hands
    // the result straight to replaceVariables, which clears the list in place — reading the old
    // values out of it lazily would be reading a list that is about to be emptied.
    Map<String, String> storedSecrets = new LinkedHashMap<>();
    for (EnvironmentVariable variable : existing.getVariables()) {
      if (variable.isSecret()) {
        storedSecrets.put(variable.getKey(), variable.getValue());
      }
    }

    for (EnvironmentVariableRequest requested : request.variables()) {
      EnvironmentVariable row = new EnvironmentVariable();
      row.setKey(requested.key().trim());
      row.setSecret(requested.isSecret());

      if (!requested.isSecret()) {
        row.setValue(requested.value() == null ? "" : requested.value());
      } else if (StringUtils.hasText(requested.value())) {
        if (!cipher.isEnabled()) {
          throw new ConflictException("error.environment.encryption-unavailable");
        }
        row.setValue(cipher.encrypt(requested.value()));
      } else {
        String carried = storedSecrets.get(row.getKey());
        if (carried == null) {
          throw new ConflictException("error.environment.secret-required", row.getKey());
        }
        row.setValue(carried);
      }
      rows.add(row);
    }
    return rows;
  }

  private static EnvironmentResponse toResponse(Environment environment) {
    List<EnvironmentVariableResponse> variables =
        environment.getVariables().stream()
            .map(
                variable ->
                    new EnvironmentVariableResponse(
                        variable.getKey(),
                        // The whole point: a secret's value does not exist on this side.
                        variable.isSecret() ? null : variable.getValue(),
                        variable.isSecret(),
                        StringUtils.hasText(variable.getValue())))
            .toList();

    return new EnvironmentResponse(
        environment.getId(),
        environment.getProjectId(),
        environment.getName(),
        environment.getBaseUrl(),
        environment.isDefault(),
        variables,
        environment.getPreludeTestCaseId(),
        environment.getCreatedAt(),
        environment.getUpdatedAt());
  }
}
