package dev.specra.api.feature.git.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestAuthConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Source Control surface end to end: connect a project to a repository, edit a file in the
 * working copy, see it in status and diff, commit it as the signed-in user, push it, and read it
 * back out of history.
 *
 * <p>The remote is a bare repository on disk over {@code file://}. That is not a shortcut — it
 * drives the same transport code a GitHub URL does, without a network or an account, and what it
 * cannot cover (a real token being accepted) is the one thing a test with a fake token could not
 * cover either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class, TestAuthConfiguration.class})
class GitApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @TempDir Path tmp;

  private String remoteUrl;

  @BeforeEach
  void seedRemote() throws Exception {
    Path bare = tmp.resolve("remote.git");
    Git.init().setDirectory(bare.toFile()).setBare(true).setInitialBranch("main").call().close();
    remoteUrl = bare.toUri().toString();

    Path seed = tmp.resolve("seed");
    try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch("main").call()) {
      Files.writeString(seed.resolve("README.md"), "# e2e\n", StandardCharsets.UTF_8);
      git.add().addFilepattern("README.md").call();
      git.commit()
          .setMessage("chore: initial commit")
          .setAuthor(new PersonIdent("Seed", "seed@specra.dev"))
          .setSign(false)
          .call();
      git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();
      git.push()
          .setRemote("origin")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main"))
          .call();
    }
  }

  /**
   * JGit holds pack files open process-wide; Windows will not delete the temp dir until it lets go.
   */
  @AfterEach
  void releaseOpenPackFiles() {
    RepositoryCache.clear();
    new WindowCacheConfig().install();
  }

  @Test
  void connectEditCommitPush() throws Exception {
    String projectId = createProject("Storefront");

    // Nothing connected yet: a state the UI renders, with a code it can branch on.
    mvc.perform(get("/api/v1/projects/{p}/repository", projectId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("repository-not-connected"));

    mvc.perform(
            put("/api/v1/projects/{p}/repository", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "provider", "GITHUB",
                            "remoteUrl", remoteUrl,
                            "defaultBranch", "main"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("GITHUB"))
        .andExpect(jsonPath("$.defaultBranch").value("main"));

    // Verify asks the remote itself, which is what makes a green answer mean something.
    mvc.perform(post("/api/v1/projects/{p}/repository/verify", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branches", org.hamcrest.Matchers.hasItem("main")));

    // First status clones on demand; the copy is a cache, so nothing had to be set up.
    mvc.perform(get("/api/v1/projects/{p}/git/status", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branch").value("main"))
        .andExpect(jsonPath("$.clean").value(true))
        .andExpect(jsonPath("$.ahead").value(0));

    mvc.perform(
            put("/api/v1/projects/{p}/git/file", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "path", "tests/auth/login.spec.ts",
                            "content", "test('login', async () => {});\n"))))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/projects/{p}/git/status", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clean").value(false))
        .andExpect(jsonPath("$.changes[0].path").value("tests/auth/login.spec.ts"))
        .andExpect(jsonPath("$.changes[0].kind").value("UNTRACKED"));

    String commit =
        mvc.perform(
                post("/api/v1/projects/{p}/git/commit", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "message",
                                "test(auth): generate the login spec",
                                "paths",
                                List.of("tests/auth/login.spec.ts")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sha").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(commit).get("sha").asText()).isNotBlank();

    // Attribution is the signed-in user, not the tool.
    mvc.perform(get("/api/v1/projects/{p}/git/history", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].message").value("test(auth): generate the login spec"))
        .andExpect(jsonPath("$.content[0].authorEmail").value(TestAuthConfiguration.EMAIL));

    mvc.perform(post("/api/v1/projects/{p}/git/push", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ahead").value(0))
        .andExpect(jsonPath("$.clean").value(true));

    // The body is compared without its line endings: the working copy is a real checkout, and
    // git rewrites those per platform when core.autocrlf says so. What matters is the text.
    mvc.perform(get("/api/v1/projects/{p}/git/file", projectId).param("path", "README.md"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("# e2e")));
  }

  @Test
  void aBranchIsCreatedCheckedOutAndRefusedWhileTheCopyIsDirty() throws Exception {
    String projectId = connectedProject("Branching");

    mvc.perform(
            post("/api/v1/projects/{p}/git/branches", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "specra/tc-104"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branch").value("specra/tc-104"));

    // The active branch is remembered on the connection, so the next request acts on it.
    mvc.perform(get("/api/v1/projects/{p}/repository", projectId))
        .andExpect(jsonPath("$.activeBranch").value("specra/tc-104"));

    mvc.perform(
            put("/api/v1/projects/{p}/git/file", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("path", "draft.txt", "content", "unsaved work\n"))))
        .andExpect(status().isOk());

    // Switching now would carry someone's uncommitted edits onto another branch.
    mvc.perform(
            post("/api/v1/projects/{p}/git/checkout", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("branch", "main"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("working-copy-dirty"));
  }

  @Test
  void aCommitOfNothingIsRefusedRatherThanCreatingAnEmptyOne() throws Exception {
    String projectId = connectedProject("Empty commit");

    mvc.perform(
            post("/api/v1/projects/{p}/git/commit", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("message", "test: nothing", "paths", List.of("README.md")))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("conflict"));
  }

  @Test
  void aPathOutsideTheWorkingCopyIsRejected() throws Exception {
    String projectId = connectedProject("Traversal");

    mvc.perform(
            get("/api/v1/projects/{p}/git/file", projectId).param("path", "../../../etc/passwd"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-parameter"));
  }

  @Test
  void gitOnAProjectWithNoRepositoryIsAnActionableConflict() throws Exception {
    String projectId = createProject("Unconnected");

    mvc.perform(get("/api/v1/projects/{p}/git/status", projectId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("repository-not-connected"));
  }

  @Test
  void anInvalidConnectionIsReportedAgainstItsFields() throws Exception {
    String projectId = createProject("Validation");

    mvc.perform(
            put("/api/v1/projects/{p}/repository", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("remoteUrl", "", "defaultBranch", ""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"))
        .andExpect(jsonPath("$.fieldErrors.provider").exists())
        .andExpect(jsonPath("$.fieldErrors.remoteUrl").exists());
  }

  private String connectedProject(String name) throws Exception {
    String projectId = createProject(name);
    mvc.perform(
            put("/api/v1/projects/{p}/repository", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "provider", "GITHUB",
                            "remoteUrl", remoteUrl,
                            "defaultBranch", "main"))))
        .andExpect(status().isOk());
    return projectId;
  }

  private String createProject(String name) throws Exception {
    String workspace =
        mvc.perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("name", name + " " + UUID.randomUUID()))))
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
    return json.readTree(project).get("id").asText();
  }

  private String body(Object value) throws IOException {
    return json.writeValueAsString(value);
  }
}
