package dev.specra.api.feature.environment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Environments against a real PostgreSQL — which also proves V12 applies on top of V11.
 *
 * <p>The assertion that matters is the one about a secret: it goes in, and no route out of this API
 * returns it. Everything else here is CRUD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class EnvironmentApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void crudRoundTripKeepingTheSecretWriteOnly() throws Exception {
    String projectId = aProject();

    String created =
        mvc.perform(
                post("/api/v1/projects/{id}/environments", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "name", "STAGING",
                                "baseUrl", "https://staging.acme.dev",
                                "variables",
                                    List.of(
                                        Map.of("key", "BASE_USER", "value", "qa@acme.dev"),
                                        Map.of(
                                            "key", "QA_PASSWORD",
                                            "value", "hunter2",
                                            "secret", true))))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("STAGING"))
            // The first environment of a project is its default, whatever the request said.
            .andExpect(jsonPath("$.isDefault").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = json.readTree(created).get("id").asText();

    // The password must not appear anywhere in the response body, under any field.
    org.assertj.core.api.Assertions.assertThat(created).doesNotContain("hunter2");

    String read =
        mvc.perform(get("/api/v1/environments/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variables[?(@.key == 'BASE_USER')].value").value("qa@acme.dev"))
            // Set, but not readable — the two things a client is allowed to know.
            .andExpect(jsonPath("$.variables[?(@.key == 'QA_PASSWORD')].valueSet").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Asserted on the node rather than through a filtered JSONPath, which unwraps a single
    // match to null and would pass just as well if the variable had disappeared entirely.
    JsonNode secret =
        StreamSupport.stream(json.readTree(read).get("variables").spliterator(), false)
            .filter(node -> "QA_PASSWORD".equals(node.get("key").asText()))
            .findFirst()
            .orElseThrow();
    org.assertj.core.api.Assertions.assertThat(secret.get("value").isNull()).isTrue();
    org.assertj.core.api.Assertions.assertThat(read).doesNotContain("hunter2");

    // Re-saving without the secret's value keeps it: the client never had it to send back.
    mvc.perform(
            put("/api/v1/environments/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "name", "STAGING",
                            "baseUrl", "https://staging-2.acme.dev",
                            "variables", List.of(Map.of("key", "QA_PASSWORD", "secret", true))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseUrl").value("https://staging-2.acme.dev"))
        .andExpect(jsonPath("$.variables[?(@.key == 'QA_PASSWORD')].valueSet").value(true));

    mvc.perform(get("/api/v1/projects/{id}/environments", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mvc.perform(delete("/api/v1/environments/{id}", id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/environments/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void aBaseUrlThatIsNotAnHttpUrlIsRejectedAgainstItsField() throws Exception {
    String projectId = aProject();

    mvc.perform(
            post("/api/v1/projects/{id}/environments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "DEV", "baseUrl", "staging.acme.dev"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.baseUrl").exists());
  }

  /** A variable name has to survive being a process environment key. */
  @Test
  void aVariableNameThatAProcessEnvironmentCouldNotHoldIsRejected() throws Exception {
    String projectId = aProject();

    mvc.perform(
            post("/api/v1/projects/{id}/environments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "name", "DEV",
                            "baseUrl", "https://dev.acme.dev",
                            "variables", List.of(Map.of("key", "not a valid key"))))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
  }

  private String aProject() throws Exception {
    String workspaceId = idOf(post("/api/v1/workspaces"), Map.of("name", "Environment QA"));
    return idOf(
        post("/api/v1/workspaces/{ws}/projects", workspaceId),
        Map.of("name", "Storefront", "key", uniqueKey()));
  }

  private String idOf(MockHttpServletRequestBuilder request, Map<String, Object> body)
      throws Exception {
    String created =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asText();
  }

  /** Each test makes its own workspace, but a derived key would still collide across reruns. */
  private static String uniqueKey() {
    return "K" + Long.toString(System.nanoTime(), 36).toUpperCase(Locale.ROOT);
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
