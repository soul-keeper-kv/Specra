package dev.specra.api.feature.workspace.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.HashMap;
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

/** Bring-your-own-key, from the outside: the key goes in once and never comes back out. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class AiAccountApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void byokRoundTrip() throws Exception {
    String workspaceId = createWorkspace();

    // Before anything is saved the workspace runs on the platform default — a state, not an error
    // body of its own: 404 with the standard code.
    mvc.perform(get("/api/v1/workspaces/{ws}/ai-account", workspaceId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));

    Map<String, Object> request = new HashMap<>();
    request.put("provider", "anthropic");
    request.put("apiKey", "sk-super-secret");
    request.put("chatModel", "claude-sonnet-4-5");
    request.put("monthlyBudgetUsd", 50.00);

    String saved =
        mvc.perform(
                put("/api/v1/workspaces/{ws}/ai-account", workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("anthropic"))
            .andExpect(jsonPath("$.keySet").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The invariant, asserted bluntly: no response field carries the key.
    assertThat(saved).doesNotContain("sk-super-secret");

    // Re-saving without the key keeps it; models change.
    request.remove("apiKey");
    request.put("chatModel", "claude-opus-4-6");
    mvc.perform(
            put("/api/v1/workspaces/{ws}/ai-account", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keySet").value(true))
        .andExpect(jsonPath("$.chatModel").value("claude-opus-4-6"));

    mvc.perform(get("/api/v1/workspaces/{ws}/ai-account", workspaceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keySet").value(true))
        // Compared as text: the field is a BigDecimal and 50.0 == 50.00 is not what equals says.
        .andExpect(jsonPath("$.monthlyBudgetUsd").exists());

    mvc.perform(delete("/api/v1/workspaces/{ws}/ai-account", workspaceId))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/workspaces/{ws}/ai-account", workspaceId))
        .andExpect(status().isNotFound());
  }

  @Test
  void anUppercaseOrInvalidProviderIsAFieldError() throws Exception {
    String workspaceId = createWorkspace();

    mvc.perform(
            put("/api/v1/workspaces/{ws}/ai-account", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"Not A Provider!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.provider").exists());
  }

  @Test
  void theAccountBelongsToAWorkspaceThatMustExist() throws Exception {
    mvc.perform(get("/api/v1/workspaces/{ws}/ai-account", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  private String createWorkspace() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"BYOK \" }"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asText();
  }
}
