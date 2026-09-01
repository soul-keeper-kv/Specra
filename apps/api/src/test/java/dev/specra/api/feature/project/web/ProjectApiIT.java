package dev.specra.api.feature.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A project from creation to deletion, against a real PostgreSQL.
 *
 * <p>The workspace is created through the API rather than seeded, because "a project cannot exist
 * without a tenant" is part of what is being asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class ProjectApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void crudRoundTrip() throws Exception {
    String workspaceId = createWorkspace("Acme QA");

    String created =
        mvc.perform(
                post("/api/v1/workspaces/{ws}/projects", workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "name", "Acme storefront",
                                "description", "Checkout journeys"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme storefront"))
            // Derived from the first word of the name, upper-cased.
            .andExpect(jsonPath("$.key").value("ACME"))
            .andExpect(jsonPath("$.engine").value("PLAYWRIGHT"))
            .andExpect(jsonPath("$.workspaceId").value(workspaceId))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = json.readTree(created).get("id").asText();

    mvc.perform(get("/api/v1/projects/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Checkout journeys"));

    mvc.perform(get("/api/v1/workspaces/{ws}/projects", workspaceId).param("q", "acme"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(id))
        .andExpect(jsonPath("$.totalElements").value(1));

    // A patch carries only what changed; the description and the key are left alone.
    mvc.perform(
            patch("/api/v1/projects/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Acme storefront v2"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme storefront v2"))
        .andExpect(jsonPath("$.description").value("Checkout journeys"))
        .andExpect(jsonPath("$.key").value("ACME"));

    mvc.perform(delete("/api/v1/projects/{id}", id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/projects/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void aSecondProjectWithTheSameNameGetsADistinctKey() throws Exception {
    String workspaceId = createWorkspace("Globex QA");

    String first = createProject(workspaceId, "Globex shop");
    String second = createProject(workspaceId, "Globex shop");

    assertThat(json.readTree(first).get("key").asText()).isEqualTo("GLOBEX");
    assertThat(json.readTree(second).get("key").asText()).isEqualTo("GLOBEX2");
  }

  @Test
  void listingAWorkspaceThatDoesNotExistIsANotFoundRatherThanAnEmptyPage() throws Exception {
    mvc.perform(get("/api/v1/workspaces/{ws}/projects", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  @Test
  void anInvalidKeyIsReportedAgainstTheFieldThatCarriesIt() throws Exception {
    String workspaceId = createWorkspace("Initech QA");

    mvc.perform(
            post("/api/v1/workspaces/{ws}/projects", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Initech shop", "key", "not lower case"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.key").exists());
  }

  private String createWorkspace(String name) throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = json.readTree(created);
    assertThat(node.get("slug").asText()).isNotBlank();
    return node.get("id").asText();
  }

  private String createProject(String workspaceId, String name) throws Exception {
    return mvc.perform(
            post("/api/v1/workspaces/{ws}/projects", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", name))))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
