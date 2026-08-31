package dev.specra.api.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.ai.dto.AskReply;
import dev.specra.api.ai.dto.AskRequest;
import dev.specra.api.ai.dto.ChatReply;
import dev.specra.api.ai.dto.ChatRequest;
import dev.specra.api.ai.dto.ProviderInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "Chat and RAG. The provider behind these endpoints is configuration, not code.")
public class AiController {

  /** Every provider whose starter this project puts on the classpath. */
  private static final List<String> BUNDLED_PROVIDERS =
      List.of("anthropic", "openai", "ollama", "transformers");

  private final ChatClient chatClient;
  private final ChatMemory chatMemory;
  private final RagService ragService;
  private final ChatModel chatModel;
  private final EmbeddingModel embeddingModel;
  private final String chatProvider;
  private final String embeddingProvider;
  private final ObjectMapper objectMapper;

  public AiController(
      ChatClient chatClient,
      ChatMemory chatMemory,
      RagService ragService,
      ChatModel chatModel,
      EmbeddingModel embeddingModel,
      @Value("${spring.ai.model.chat}") String chatProvider,
      @Value("${spring.ai.model.embedding}") String embeddingProvider,
      ObjectMapper objectMapper) {
    this.chatClient = chatClient;
    this.chatMemory = chatMemory;
    this.ragService = ragService;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.chatProvider = chatProvider;
    this.embeddingProvider = embeddingProvider;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/providers")
  @Operation(summary = "Report the active chat and embedding providers")
  public ProviderInfo providers() {
    return new ProviderInfo(
        chatProvider,
        embeddingProvider,
        chatModel.getClass().getSimpleName(),
        embeddingModel.getClass().getSimpleName(),
        embeddingModel.dimensions(),
        BUNDLED_PROVIDERS);
  }

  @PostMapping("/chat")
  @Operation(summary = "Ask the model, keeping conversation history in Postgres")
  public ChatReply chat(@Valid @RequestBody ChatRequest request) {
    String conversationId = request.conversationIdOrDefault();
    ChatResponse response =
        chatClient
            .prompt()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(request.message())
            .call()
            .chatResponse();

    String content =
        response == null || response.getResult() == null
            ? ""
            : response.getResult().getOutput().getText();
    String model = response == null ? null : response.getMetadata().getModel();
    return new ChatReply(content, conversationId, chatProvider, model);
  }

  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Same as /chat but streams tokens over server-sent events",
      description =
          "Each `token` event carries the token as a JSON string. Clients must JSON.parse it.")
  public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
    String conversationId = request.conversationIdOrDefault();
    return chatClient
        .prompt()
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .user(request.message())
        .stream()
        .content()
        .map(token -> ServerSentEvent.builder(asJson(token)).event("token").build())
        .concatWithValues(ServerSentEvent.<String>builder("\"\"").event("done").build());
  }

  /**
   * Model tokens routinely start with a space (" world") and can contain newlines — both of which
   * plain SSE framing mangles, because a receiver must strip one space after {@code data:} and a
   * newline would split the payload across frames. Encoding each token as a JSON string sidesteps
   * both: the value is quoted, newlines become {@code \n}, and the client recovers it exactly with
   * JSON.parse.
   */
  private String asJson(String token) {
    try {
      return objectMapper.writeValueAsString(token);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode token", e);
    }
  }

  @DeleteMapping("/chat/{conversationId}")
  @Operation(summary = "Forget one conversation")
  public ResponseEntity<Void> clear(@PathVariable String conversationId) {
    chatMemory.clear(conversationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/ask")
  @Operation(summary = "RAG: retrieve indexed note chunks from pgvector, then answer from them")
  public AskReply ask(@Valid @RequestBody AskRequest request) {
    return ragService.ask(request);
  }

  @GetMapping("/retrieve")
  @Operation(summary = "Similarity search only, no generation — shows what RAG would feed the model")
  public List<AskReply.Source> retrieve(
      @RequestParam String q,
      @RequestParam(defaultValue = "4") int topK,
      @RequestParam(defaultValue = "0.5") double threshold) {
    return ragService.retrieve(q, topK, threshold);
  }
}
