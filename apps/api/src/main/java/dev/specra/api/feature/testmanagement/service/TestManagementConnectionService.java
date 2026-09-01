package dev.specra.api.feature.testmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.ConflictException;
import dev.specra.api.core.error.ResourceNotFoundException;
import dev.specra.api.core.security.Permission;
import dev.specra.api.core.security.SecretsCipher;
import dev.specra.api.feature.testmanagement.domain.TestManagementBindingRepository;
import dev.specra.api.feature.testmanagement.domain.TestManagementConnection;
import dev.specra.api.feature.testmanagement.domain.TestManagementConnectionRepository;
import dev.specra.api.feature.testmanagement.dto.TestManagementConnectionRequest;
import dev.specra.api.feature.testmanagement.dto.TestManagementConnectionResponse;
import dev.specra.api.feature.workspace.service.WorkspaceService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Provider-neutral encrypted connection storage. */
@Service
@Transactional(readOnly = true)
public class TestManagementConnectionService {
  private static final int MAX_ENTRIES = 20;
  private static final int MAX_KEY_LENGTH = 64;
  private static final int MAX_VALUE_LENGTH = 2000;

  private final TestManagementConnectionRepository repository;
  private final TestManagementBindingRepository bindings;
  private final WorkspaceService workspaces;
  private final TestManagementProviders providers;
  private final SecretsCipher cipher;
  private final ObjectMapper objectMapper;

  public TestManagementConnectionService(
      TestManagementConnectionRepository repository,
      TestManagementBindingRepository bindings,
      WorkspaceService workspaces,
      TestManagementProviders providers,
      SecretsCipher cipher,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.bindings = bindings;
    this.workspaces = workspaces;
    this.providers = providers;
    this.cipher = cipher;
    this.objectMapper = objectMapper;
  }

  public List<TestManagementConnectionResponse> list(UUID workspaceId) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_VIEW);
    return repository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
        .map(TestManagementConnectionService::response)
        .toList();
  }

  @Transactional
  public TestManagementConnectionResponse create(
      UUID workspaceId, TestManagementConnectionRequest request) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_UPDATE);
    String name = request.name().trim();
    String providerKind = request.provider().trim().toLowerCase(Locale.ROOT);
    if (repository.existsByWorkspaceIdAndName(workspaceId, name)) {
      throw new ConflictException("error.test-management.connection-duplicate-name", name);
    }
    if (!cipher.isEnabled()) {
      throw new ConflictException("error.test-management.encryption-unavailable");
    }

    Map<String, String> configuration = clean(request.configuration());
    Map<String, String> credentials = clean(request.credentials());
    providers.require(providerKind).validate(configuration, credentials);

    TestManagementConnection connection = new TestManagementConnection();
    connection.setWorkspaceId(workspaceId);
    connection.setName(name);
    connection.setProvider(providerKind);
    connection.setConfiguration(configuration);
    connection.setCredentialsCipher(cipher.encrypt(write(credentials)));
    return response(repository.save(connection));
  }

  @Transactional
  public void delete(UUID workspaceId, UUID id) {
    workspaces.requireAccess(workspaceId, Permission.WORKSPACE_UPDATE);
    TestManagementConnection connection = require(workspaceId, id);
    if (bindings.existsByConnectionId(id)) {
      throw new ConflictException("error.test-management.connection-in-use");
    }
    repository.delete(connection);
  }

  ConnectionMaterial material(UUID workspaceId, UUID connectionId) {
    TestManagementConnection connection = require(workspaceId, connectionId);
    return new ConnectionMaterial(
        connection,
        providers.require(connection.getProvider()),
        new TestManagementProvider.ProviderConnection(
            Map.copyOf(connection.getConfiguration()), read(connection.getCredentialsCipher())));
  }

  TestManagementConnection require(UUID workspaceId, UUID id) {
    return repository
        .findByIdAndWorkspaceId(id, workspaceId)
        .orElseThrow(
            () -> new ResourceNotFoundException("resource.test-management-connection", id));
  }

  private String write(Map<String, String> credentials) {
    try {
      return objectMapper.writeValueAsString(credentials);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not encode integration credentials", e);
    }
  }

  private Map<String, String> read(String ciphertext) {
    try {
      return objectMapper.readValue(
          cipher.decrypt(ciphertext), new TypeReference<Map<String, String>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not decode integration credentials", e);
    }
  }

  private static Map<String, String> clean(Map<String, String> source) {
    if (source.size() > MAX_ENTRIES) {
      throw new ConflictException("error.test-management.too-many-settings", MAX_ENTRIES);
    }
    Map<String, String> result = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> {
          if (!StringUtils.hasText(key)
              || key.length() > MAX_KEY_LENGTH
              || value == null
              || value.length() > MAX_VALUE_LENGTH) {
            throw new ConflictException("error.test-management.invalid-setting");
          }
          result.put(key.trim(), value.trim());
        });
    return Map.copyOf(result);
  }

  private static TestManagementConnectionResponse response(TestManagementConnection connection) {
    return new TestManagementConnectionResponse(
        connection.getId(),
        connection.getName(),
        connection.getProvider(),
        Map.copyOf(connection.getConfiguration()),
        StringUtils.hasText(connection.getCredentialsCipher()),
        connection.getUpdatedAt());
  }

  record ConnectionMaterial(
      TestManagementConnection connection,
      TestManagementProvider provider,
      TestManagementProvider.ProviderConnection providerConnection) {}
}
