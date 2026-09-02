package dev.specra.api.feature.testmodel.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A person editing the IR by hand — {@code PUT …/model}.
 *
 * <p>The two assertions that matter are opposites: an edit a reviewer makes is stored as the next
 * version rather than overwriting what it corrected, and an edit that breaks the contract is
 * refused with the violations rather than stored because a human rather than a model typed it. If
 * the hand-written path were the lenient one, "the stored IR is always valid" would be a hope.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class TestModelEditApiIT {

  /** The shared fixture, so this test asserts against the same bytes every other consumer does. */
  private static final Path MINIMAL =
      Path.of("..", "..", "packages", "test-model", "fixtures", "valid", "minimal.json");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void anEditedModelIsStoredAsTheNextVersion() throws Exception {
    String testCaseId = aTestCase();
    String document = Files.readString(MINIMAL);

    mvc.perform(
            put("/api/v1/test-cases/{id}/model", testCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(document))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.document.name").value("The home page loads"));

    // The same case edited again: version 2, and version 1 still readable behind it.
    String renamed = document.replace("The home page loads", "The home page still loads");
    mvc.perform(
            put("/api/v1/test-cases/{id}/model", testCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renamed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));

    mvc.perform(get("/api/v1/test-cases/{id}/model", testCaseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document.name").value("The home page still loads"));

    mvc.perform(get("/api/v1/test-cases/{id}/model/versions/{version}", testCaseId, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document.name").value("The home page loads"));
  }

  /**
   * A document that no longer says what a test is. It passes the schema — every field is the right
   * shape — and is refused by the semantic layer, which is the layer a hand edit is most likely to
   * walk into.
   */
  @Test
  void anEditThatAssertsNothingIsRefusedWithItsViolations() throws Exception {
    String testCaseId = aTestCase();
    String assertionless =
        """
        {
          "irVersion": 1,
          "name": "The home page loads",
          "steps": [
            {
              "id": "s1",
              "sourceStepIds": ["ts-1"],
              "action": "navigate",
              "target": { "page": "HomePage" }
            }
          ]
        }
        """;

    mvc.perform(
            put("/api/v1/test-cases/{id}/model", testCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(assertionless))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("test-model-invalid"))
        .andExpect(jsonPath("$.violations").isArray())
        .andExpect(jsonPath("$.violations[0].message").exists());

    // Nothing was stored: the case still has no model at all.
    mvc.perform(get("/api/v1/test-cases/{id}/model", testCaseId)).andExpect(status().isNotFound());
  }

  @Test
  void anEditThatBreaksTheSchemaIsRefused() throws Exception {
    String testCaseId = aTestCase();

    mvc.perform(
            put("/api/v1/test-cases/{id}/model", testCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"irVersion\": 1}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("test-model-invalid"));
  }

  /** A workspace, a project and a case to hang a model on. */
  private String aTestCase() throws Exception {
    String workspaceId = idOf(post("/api/v1/workspaces"), Map.of("name", "Model edit QA"));
    String projectId =
        idOf(
            post("/api/v1/workspaces/{ws}/projects", workspaceId),
            Map.of("name", "Storefront", "key", uniqueKey()));
    return idOf(
        post("/api/v1/projects/{id}/test-cases", projectId),
        Map.of(
            "title",
            "The home page loads",
            "steps",
            List.of(Map.of("action", "Open the home page", "expected", "It renders"))));
  }

  private String idOf(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      Map<String, Object> body)
      throws Exception {
    String created =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(created).get("id").asText();
  }

  /**
   * Project keys are unique per workspace and each test makes its own workspace, but the derived
   * key would collide across reruns inside one container otherwise.
   */
  private static String uniqueKey() {
    return "K" + Long.toString(System.nanoTime(), 36).toUpperCase(java.util.Locale.ROOT);
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
