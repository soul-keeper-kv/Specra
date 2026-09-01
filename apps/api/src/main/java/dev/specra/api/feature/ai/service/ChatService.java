package dev.specra.api.feature.ai.service;

import dev.specra.api.feature.ai.dto.ChatReply;
import dev.specra.api.feature.ai.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Conversational chat: one turn in, one answer out, history kept per conversation id.
 *
 * <p>The controller used to hold this itself, which meant the web layer knew about advisors, memory
 * parameters and Spring AI's response shape. It now knows only about HTTP.
 */
@Service
public class ChatService {

  private final ChatClient chatClient;
  private final ChatMemory chatMemory;
  private final AiProviders providers;

  public ChatService(ChatClient chatClient, ChatMemory chatMemory, AiProviders providers) {
    this.chatClient = chatClient;
    this.chatMemory = chatMemory;
    this.providers = providers;
  }

  public ChatReply answer(ChatRequest request) {
    String conversationId = request.conversationIdOrDefault();
    ChatResponse response = prompt(request, conversationId).call().chatResponse();

    String content =
        response == null || response.getResult() == null
            ? ""
            : response.getResult().getOutput().getText();
    String model = response == null ? null : response.getMetadata().getModel();
    return new ChatReply(content, conversationId, providers.chat(), model);
  }

  /**
   * The same turn as {@link #answer}, emitted token by token. Framing those tokens for the wire is
   * the job of the controller — this returns the text of the model and nothing else.
   */
  public Flux<String> streamTokens(ChatRequest request) {
    return prompt(request, request.conversationIdOrDefault()).stream().content();
  }

  public void clear(String conversationId) {
    chatMemory.clear(conversationId);
  }

  private ChatClient.ChatClientRequestSpec prompt(ChatRequest request, String conversationId) {
    return chatClient
        .prompt()
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .user(request.message());
  }
}
