package dev.specra.api.feature.ai.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import dev.specra.api.core.error.ProblemFactory;
import dev.specra.api.feature.ai.dto.AskReply;
import dev.specra.api.feature.ai.dto.AskRequest;
import dev.specra.api.feature.ai.dto.ChatReply;
import dev.specra.api.feature.ai.dto.ChatRequest;
import dev.specra.api.feature.ai.dto.ProviderInfo;
import dev.specra.api.feature.ai.service.AiProviders;
import dev.specra.api.feature.ai.service.ChatService;
import dev.specra.api.feature.ai.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * HTTP only: parse, delegate, frame the answer. Every decision about models, memory and retrieval
 * lives in {@link ChatService}, {@link RagService} and {@link AiProviders}.
 */
@RestController
@RequestMapping("/api/ai")
@Validated
@Tag(
    name = "AI",
    description = "Chat and RAG. The provider behind these endpoints is configuration, not code.")
public class AiController {

  private final ChatService chatService;
  private final RagService ragService;
  private final AiProviders providers;
  private final ProblemFactory problems;
  private final ObjectMapper objectMapper;

  public AiController(
      ChatService chatService,
      RagService ragService,
      AiProviders providers,
      ProblemFactory problems,
      ObjectMapper objectMapper) {
    this.chatService = chatService;
    this.ragService = ragService;
    this.providers = providers;
    this.problems = problems;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/providers")
  @Operation(summary = "Report the active chat and embedding providers")
  public ProviderInfo providers() {
    return providers.info();
  }

  @PostMapping("/chat")
  @Operation(summary = "Ask the model, keeping conversation history in Postgres")
  public ChatReply chat(@Valid @RequestBody ChatRequest request) {
    return chatService.answer(request);
  }

  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(
      summary = "Same as /chat but streams tokens over server-sent events",
      description =
          "Each `token` event carries the token as a JSON string. Clients must JSON.parse it.")
  public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
    return chatService
        .streamTokens(request)
        .map(token -> ServerSentEvent.builder(asJson(token)).event("token").build())
        .concatWithValues(ServerSentEvent.<String>builder("\"\"").event("done").build())
        .onErrorResume(failure -> Flux.just(errorEvent(failure)));
  }

  /**
   * A stream that has already sent its first byte cannot be given a problem document: the status
   * line and headers are long gone, and throwing here would leave the client with a truncated
   * stream and no reason for it. So the failure becomes a final {@code error} event whose payload
   * is the same RFC 9457 document the non-streaming endpoints return — the client branches on
   * {@code code} exactly as it does everywhere else.
   *
   * <p>A failure raised <em>before</em> the first token never reaches here: {@code
   * AiFailures.guardStream} checks credentials eagerly, so an unconfigured provider fails while the
   * response is still uncommitted and is rendered as an ordinary problem document with its own
   * status.
   */
  private ServerSentEvent<String> errorEvent(Throwable failure) {
    ErrorCode code =
        failure instanceof BusinessException business
            ? business.errorCode()
            : ErrorCode.AI_PROVIDER_ERROR;
    ProblemDetail problem =
        failure instanceof BusinessException business
            ? problems.of(business.errorCode(), business.messageKey(), business.messageArgs())
            : problems.of(code, code.detailKey(), providers.chat());
    return ServerSentEvent.builder(write(problem)).event("error").build();
  }

  private String write(ProblemDetail problem) {
    try {
      return objectMapper.writeValueAsString(problem);
    } catch (JsonProcessingException e) {
      // The stream is already open; a bare code keeps the client on the documented shape.
      return "{\"code\":\"" + ErrorCode.INTERNAL_ERROR.slug() + "\"}";
    }
  }

  /**
   * Model tokens routinely start with a space (" world") and can contain newlines — both of which
   * plain SSE framing mangles, because a receiver must strip one space after {@code data:} and a
   * newline would split the payload across frames. Encoding each token as a JSON string sidesteps
   * both: the value is quoted, newlines become {@code \n}, and the client recovers it exactly with
   * JSON.parse. That is framing, not chat, which is why it stays in the controller.
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
    chatService.clear(conversationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/ask")
  @Operation(summary = "RAG: retrieve indexed note chunks from pgvector, then answer from them")
  public AskReply ask(@Valid @RequestBody AskRequest request) {
    return ragService.ask(request);
  }

  /**
   * The bounds match {@link AskRequest}: the same query reaching the store through a different
   * endpoint must not be able to ask for ten thousand neighbours.
   */
  @GetMapping("/retrieve")
  @Operation(
      summary = "Similarity search only, no generation — shows what RAG would feed the model")
  public List<AskReply.Source> retrieve(
      @RequestParam @NotBlank(message = "{validation.ask.question.required}") String q,
      @RequestParam(defaultValue = "4")
          @Min(value = 1, message = "{validation.ask.top-k.range}") @Max(value = 20, message = "{validation.ask.top-k.range}") int topK,
      @RequestParam(defaultValue = "0.5")
          @DecimalMin(value = "0.0", message = "{validation.ask.threshold.range}") @DecimalMax(value = "1.0", message = "{validation.ask.threshold.range}") double threshold) {
    return ragService.retrieve(q, topK, threshold);
  }
}
