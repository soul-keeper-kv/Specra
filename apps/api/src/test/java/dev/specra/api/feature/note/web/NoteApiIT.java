package dev.specra.api.feature.note.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class NoteApiIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String body(Object o) throws Exception {
    return json.writeValueAsString(o);
  }

  @Test
  void crudRoundTrip() throws Exception {
    String created =
        mvc.perform(
                post("/api/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "title", "Kickoff notes",
                                "content", "We agreed to ship the pgvector spike first.",
                                "tags", Set.of("Meeting", "q3")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Kickoff notes"))
            // replaceTags lowercases and trims
            .andExpect(jsonPath("$.tags", org.hamcrest.Matchers.hasItem("meeting")))
            .andExpect(jsonPath("$.indexedAt").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = json.readTree(created).get("id").asText();

    mvc.perform(get("/api/notes/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("We agreed to ship the pgvector spike first."));

    mvc.perform(get("/api/notes").param("q", "pgvector"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));

    mvc.perform(get("/api/notes").param("tag", "meeting"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mvc.perform(get("/api/notes").param("q", "nothing-matches-this"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            put("/api/notes/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "title", "Kickoff notes v2",
                            "content", "Decision reversed: start with the API contract.",
                            "tags", Set.of("meeting")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Kickoff notes v2"));

    mvc.perform(delete("/api/notes/{id}", id)).andExpect(status().isNoContent());
    mvc.perform(get("/api/notes/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("resource-not-found"));
  }

  @Test
  void validationFailureReportsTheOffendingField() throws Exception {
    mvc.perform(
            post("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("title", "", "content", "body"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("validation-failed"))
        // The message comes from the bundle, not from Hibernate Validator's English default.
        .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
  }

  @Test
  void indexingWritesChunksToPgvectorAndTheyComeBackFromSimilaritySearch() throws Exception {
    String created =
        mvc.perform(
                post("/api/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            Map.of(
                                "title",
                                "Vector store choice",
                                "content",
                                "We picked pgvector because Postgres already holds the "
                                    + "business data and HNSW indexing is good enough.",
                                "tags",
                                Set.of("architecture")))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(created).get("id").asText();

    mvc.perform(post("/api/notes/{id}/index", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indexedAt").exists());

    String hits =
        mvc.perform(
                get("/api/ai/retrieve")
                    .param("q", "pgvector Postgres HNSW")
                    .param("topK", "5")
                    .param("threshold", "0.0"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode arr = json.readTree(hits);
    assertThat(arr).isNotEmpty();
    assertThat(arr.get(0).get("noteId").asText()).isEqualTo(id);
    assertThat(arr.get(0).get("title").asText()).isEqualTo("Vector store choice");

    // Deleting the note must also drop its embeddings.
    mvc.perform(delete("/api/notes/{id}", id)).andExpect(status().isNoContent());

    JsonNode after =
        json.readTree(
            mvc.perform(
                    get("/api/ai/retrieve")
                        .param("q", "pgvector Postgres HNSW")
                        .param("threshold", "0.0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(after).isEmpty();
  }

  @Test
  void providersEndpointReportsWhatIsActuallyWired() throws Exception {
    mvc.perform(get("/api/ai/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatProvider").value("none"))
        .andExpect(jsonPath("$.embeddingModelType").value("HashingEmbeddingModel"))
        .andExpect(jsonPath("$.embeddingDimensions").value(384))
        .andExpect(jsonPath("$.availableProviders", org.hamcrest.Matchers.hasItem("anthropic")));
  }

  @Test
  void chatGoesThroughTheChatClientAndKeepsHistory() throws Exception {
    mvc.perform(
            post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("message", "hello there", "conversationId", "it-test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationId").value("it-test"))
        .andExpect(jsonPath("$.content", org.hamcrest.Matchers.containsString("hello there")));

    mvc.perform(delete("/api/ai/chat/{id}", "it-test")).andExpect(status().isNoContent());
  }
}
