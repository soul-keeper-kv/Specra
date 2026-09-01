package dev.specra.api.feature.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole sign-in surface, from the outside and with nothing stubbed out.
 *
 * <p>Deliberately does <em>not</em> import {@code TestAuthConfiguration}: every other integration
 * test is signed in by default, and this one is about what happens before that. It asserts on the
 * two things that are easy to get subtly wrong and impossible to notice from a happy path — that a
 * refusal is a problem document with the right {@code code}, and that a refresh token really is
 * single-use.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class AuthApiIT {

  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void registerThenSignInThenReadTheAccountBack() throws Exception {
    String email = uniqueEmail();
    JsonNode registered = register(email, "Nguyen QA");

    assertThat(registered.get("tokenType").asText()).isEqualTo("Bearer");
    assertThat(registered.get("accessToken").asText()).isNotBlank();
    assertThat(registered.get("refreshToken").asText()).isNotBlank();
    assertThat(registered.get("user").get("email").asText()).isEqualTo(email);

    JsonNode signedIn = login(email, PASSWORD);

    mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(signedIn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.displayName").value("Nguyen QA"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        // Nothing that could be replayed ever leaves the API.
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  /**
   * Registration is what gives a new account somewhere to work, through an event the workspace
   * feature listens to after commit. Without it the first screen after signing up is empty.
   */
  @Test
  void aNewAccountLandsInAWorkspaceItOwns() throws Exception {
    JsonNode registered = register(uniqueEmail(), "Owner");

    mvc.perform(get("/api/v1/workspaces").header(HttpHeaders.AUTHORIZATION, bearer(registered)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].role").value("OWNER"));
  }

  @Test
  void noCredentialsIsAProblemDocumentAndNotAnEmptyBody() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }

  /**
   * A token that was presented and did not verify is a different situation from no token at all:
   * the web app answers this one by refreshing rather than by sending the user to sign in.
   */
  @Test
  void aTokenThatDoesNotVerifyIsReportedAsSuchSoTheClientCanRefresh() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid-token"));
  }

  @Test
  void aWrongPasswordSaysNothingAboutWhetherTheAddressExists() throws Exception {
    String email = uniqueEmail();
    register(email, "QA");

    String known =
        mvc.perform(loginRequest(email, "not the password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid-credentials"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String unknown =
        mvc.perform(loginRequest(uniqueEmail(), "not the password"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid-credentials"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(json.readTree(known).get("detail")).isEqualTo(json.readTree(unknown).get("detail"));
  }

  /**
   * Vietnamese, because an English-only assertion passes even when the bundle stops being wired.
   */
  @Test
  void anAddressThatIsAlreadyRegisteredIsRefusedInTheCallersLanguage() throws Exception {
    String email = uniqueEmail();
    register(email, "QA");

    mvc.perform(
            post("/api/v1/auth/register")
                .header("Accept-Language", "vi")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "displayName", "QA", "password", PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("email-already-used"))
        .andExpect(jsonPath("$.title").value("Email đã được đăng ký"));
  }

  /**
   * Rotation, and what makes it worth doing: the token that was exchanged is dead, and presenting
   * it again is treated as a replay rather than as a stale copy.
   */
  @Test
  void aRefreshTokenIsSingleUseAndReplayingItEndsEverySession() throws Exception {
    String email = uniqueEmail();
    JsonNode first = register(email, "QA");
    String original = first.get("refreshToken").asText();

    JsonNode second =
        json.readTree(
            mvc.perform(refreshRequest(original))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String rotated = second.get("refreshToken").asText();
    assertThat(rotated).isNotEqualTo(original);

    // The one that was exchanged no longer works...
    mvc.perform(refreshRequest(original))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid-token"));

    // ...and because two parties holding it is indistinguishable from a theft, its successor
    // is revoked along with it.
    mvc.perform(refreshRequest(rotated))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid-token"));
  }

  @Test
  void signingOutRevokesTheRefreshTokenItWasGiven() throws Exception {
    JsonNode registered = register(uniqueEmail(), "QA");
    String refreshToken = registered.get("refreshToken").asText();

    mvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(registered))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("refreshToken", refreshToken))))
        .andExpect(status().isNoContent());

    mvc.perform(refreshRequest(refreshToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid-token"));
  }

  @Test
  void registrationValidatesEveryFieldAndNamesTheOffendingOne() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(Map.of("email", "not-an-email", "displayName", "", "password", "short"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.email").exists())
        .andExpect(jsonPath("$.fieldErrors.displayName").exists())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Unique per call so the tests do not have to run in an order, or clean up after each other. */
  private static String uniqueEmail() {
    return "auth-it-" + UUID.randomUUID() + "@specra.dev";
  }

  private JsonNode register(String email, String displayName) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "email", email,
                                "displayName", displayName,
                                "password", PASSWORD))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response);
  }

  private JsonNode login(String email, String password) throws Exception {
    return json.readTree(
        mvc.perform(loginRequest(email, password))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private org.springframework.test.web.servlet.RequestBuilder loginRequest(
      String email, String password) throws Exception {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(Map.of("email", email, "password", password)));
  }

  private org.springframework.test.web.servlet.RequestBuilder refreshRequest(String refreshToken)
      throws Exception {
    return post("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(Map.of("refreshToken", refreshToken)));
  }

  private static String bearer(JsonNode tokens) {
    return "Bearer " + tokens.get("accessToken").asText();
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
