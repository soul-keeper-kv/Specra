package dev.specra.api.feature.testcase.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.List;
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
 * The authoring milestone's contract, end to end: a case is created under a project with a minted
 * reference, edited with its whole step list replaced, filtered in the list, and deleted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class TestCaseApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void authoringRoundTrip() throws Exception {
    String projectId = createProject("Storefront");

    String created =
        mvc.perform(
                post("/api/v1/projects/{p}/test-cases", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "title",
                                "Đăng nhập hợp lệ",
                                "priority",
                                "HIGH",
                                "steps",
                                List.of(
                                    Map.of("action", "Mở trang đăng nhập"),
                                    Map.of(
                                        "action", "Nhập email và mật khẩu",
                                        "expected", "Nút Đăng nhập bật")),
                                "tags",
                                List.of("Auth", "smoke")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reference").value("TC-1"))
            .andExpect(jsonPath("$.automationStatus").value("NOT_AUTOMATED"))
            .andExpect(jsonPath("$.outOfDate").value(false))
            .andExpect(jsonPath("$.steps[0].position").value(1))
            .andExpect(jsonPath("$.steps[1].expected").value("Nút Đăng nhập bật"))
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.hasItem("auth")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = json.readTree(created).get("id").asText();

    // The second case gets the next number of the same sequence.
    mvc.perform(
            post("/api/v1/projects/{p}/test-cases", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("title", "Đăng xuất"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reference").value("TC-2"));

    mvc.perform(get("/api/v1/projects/{p}/test-cases", projectId).param("q", "đăng nhập"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].reference").value("TC-1"));

    mvc.perform(get("/api/v1/projects/{p}/test-cases", projectId).param("tag", "smoke"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // Replacing the whole case: fewer steps, renumbered from one, reference untouched.
    mvc.perform(
            put("/api/v1/test-cases/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "title",
                            "Đăng nhập hợp lệ (rút gọn)",
                            "steps",
                            List.of(Map.of("action", "Đăng nhập bằng tài khoản demo"))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reference").value("TC-1"))
        .andExpect(jsonPath("$.steps.length()").value(1))
        .andExpect(jsonPath("$.steps[0].position").value(1))
        // Nothing was ever generated from this case, so nothing is out of date.
        .andExpect(jsonPath("$.outOfDate").value(false));

    mvc.perform(delete("/api/v1/test-cases/{id}", id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/test-cases/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  @Test
  void aStepWithoutAnActionIsAFieldError() throws Exception {
    String projectId = createProject("Validation");

    mvc.perform(
            post("/api/v1/projects/{p}/test-cases", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(Map.of("title", "Thiếu bước", "steps", List.of(Map.of("action", "  "))))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors['steps[0].action']").exists());
  }

  @Test
  void creatingUnderAMissingProjectIsANotFound() throws Exception {
    mvc.perform(
            post("/api/v1/projects/{p}/test-cases", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("title", "Mồ côi"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  private String createProject(String name) throws Exception {
    String workspace =
        mvc.perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", name + " WS " + UUID.randomUUID()))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workspaceId = json.readTree(workspace).get("id").asText();

    String project =
        mvc.perform(
                post("/api/v1/workspaces/{ws}/projects", workspaceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String projectId = json.readTree(project).get("id").asText();
    assertThat(projectId).isNotBlank();
    return projectId;
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
