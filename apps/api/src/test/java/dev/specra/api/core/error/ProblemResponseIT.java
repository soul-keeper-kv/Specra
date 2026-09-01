package dev.specra.api.core.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specra.api.core.logging.MdcKeys;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The error contract, asserted from the outside.
 *
 * <p>Two things are being pinned down. First, that every failure — ours and Spring MVC's — comes
 * back as one {@code application/problem+json} shape with a stable {@code code}, because that is
 * what the web app branches on. Second, that the text really is translated: if the Vietnamese
 * assertions ever start passing with English strings, the message bundle has silently stopped being
 * wired in and nobody would notice from the English tests alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class ProblemResponseIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String UUID_FORM =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired MockMvc mvc;

  @Test
  void notFoundCarriesTheCodeTypeAndCorrelationIds() throws Exception {
    mvc.perform(get("/api/v1/test-cases/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://specra.dev/problems/resource-not-found"))
        .andExpect(jsonPath("$.code").value("resource-not-found"))
        .andExpect(jsonPath("$.title").value("Not found"))
        .andExpect(jsonPath("$.timestamp").exists())
        // Set by CorrelationIdFilter, and repeated in the body so a screenshot of an
        // error is enough to find the log line.
        .andExpect(header().exists(MdcKeys.REQUEST_ID_HEADER))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void aCallerSuppliedRequestIdIsEchoedBackRatherThanReplaced() throws Exception {
    mvc.perform(
            get("/api/v1/test-cases/{id}", UUID.randomUUID())
                .header(MdcKeys.REQUEST_ID_HEADER, "web-42_abc"))
        .andExpect(status().isNotFound())
        .andExpect(header().string(MdcKeys.REQUEST_ID_HEADER, "web-42_abc"))
        .andExpect(jsonPath("$.requestId").value("web-42_abc"));
  }

  @Test
  void aRequestIdThatCouldPolluteTheLogIsDiscarded() throws Exception {
    mvc.perform(
            get("/api/v1/test-cases/{id}", UUID.randomUUID())
                .header(MdcKeys.REQUEST_ID_HEADER, "bad\nINJECTED LOG LINE"))
        .andExpect(status().isNotFound())
        // Replaced by a fresh UUID, so the newline never reaches a log line.
        .andExpect(
            header()
                .string(
                    MdcKeys.REQUEST_ID_HEADER, org.hamcrest.Matchers.matchesPattern(UUID_FORM)));
  }

  @Test
  void acceptLanguageTranslatesTitleAndDetail() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(get("/api/v1/test-cases/{id}", id).header("Accept-Language", "vi"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Không tìm thấy"))
        // The resource noun is translated too, not spliced in as English.
        .andExpect(jsonPath("$.detail").value("Không tồn tại Ca kiểm thử " + id + "."))
        // The code is the one thing that must not change with the language.
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  @Test
  void theLangQueryParameterWorksWithoutAHeader() throws Exception {
    mvc.perform(get("/api/v1/test-cases/{id}", UUID.randomUUID()).param("lang", "vi"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Không tìm thấy"));
  }

  @Test
  void anUnsupportedLanguageFallsBackToEnglishRatherThanToRawKeys() throws Exception {
    mvc.perform(get("/api/v1/test-cases/{id}", UUID.randomUUID()).header("Accept-Language", "de"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Not found"));
  }

  @Test
  void validationMessagesAreTranslatedPerField() throws Exception {
    mvc.perform(
            post("/api/v1/workspaces")
                .header("Accept-Language", "vi")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"slug\":\"KHÔNG hợp lệ\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.name").value("Tên là bắt buộc"))
        .andExpect(
            jsonPath("$.fieldErrors.slug")
                .value("Chỉ dùng chữ thường, chữ số và dấu gạch nối đơn"));
  }

  @Test
  void unparseableJsonIsAMalformedRequestNotAServerError() throws Exception {
    mvc.perform(
            post("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json at all"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("malformed-request"));
  }

  @Test
  void anUnknownPathIsProblemJsonRatherThanTheContainerErrorPage() throws Exception {
    mvc.perform(get("/api/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("endpoint-not-found"));
  }

  @Test
  void theWrongHttpMethodIsReportedWithItsOwnCode() throws Exception {
    mvc.perform(patch("/api/v1/workspaces/{id}", UUID.randomUUID()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("method-not-allowed"));
  }

  @Test
  void aParameterOfTheWrongTypeIsRejectedAsInvalidNotAsAServerError() throws Exception {
    mvc.perform(get("/api/ai/retrieve").param("q", "anything").param("topK", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-parameter"));
  }

  /**
   * A conflict the user can act on, so the sentence has to name the key they chose — and name it in
   * their language, while {@code code} stays the string the web app branches on.
   */
  @Test
  void aDuplicateProjectKeyIsAConflictTranslatedInBothLanguages() throws Exception {
    String workspaceId =
        idOf(
            mvc.perform(
                    post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicate key QA\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    String sameKey = "{\"name\":\"Anything\",\"key\":\"DUP\"}";
    mvc.perform(
            post("/api/v1/workspaces/{ws}/projects", workspaceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sameKey))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/workspaces/{ws}/projects", workspaceId)
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sameKey))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("conflict"))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(
            jsonPath("$.detail")
                .value("The key \"DUP\" is already used by another project in this workspace."));

    mvc.perform(
            post("/api/v1/workspaces/{ws}/projects", workspaceId)
                .header("Accept-Language", "vi")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sameKey))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("conflict"))
        .andExpect(jsonPath("$.title").value("Xung đột dữ liệu"))
        .andExpect(
            jsonPath("$.detail")
                .value("Mã \"DUP\" đã được một dự án khác trong không gian làm việc này sử dụng."));
  }

  private static String idOf(String responseBody) {
    Matcher matcher = Pattern.compile("\"id\":\"(" + UUID_FORM + ")\"").matcher(responseBody);
    assertThat(matcher.find()).as("no id in %s", responseBody).isTrue();
    return matcher.group(1);
  }
}
