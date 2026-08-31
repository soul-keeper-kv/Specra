package dev.specra.api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Nothing here names a vendor.
 *
 * <p>Every provider starter (Anthropic, OpenAI, Ollama, local ONNX transformers) sits on the
 * classpath, but Spring AI only instantiates the one selected by {@code spring.ai.model.chat} and
 * {@code spring.ai.model.embedding}. So exactly one {@link ChatModel} bean exists at runtime and
 * this class injects it by type — swapping providers is an env var, not a code change.
 */
@Configuration
public class AiConfig {

  private final String systemPrompt;

  public AiConfig(@Value("${specra.ai.system-prompt}") String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  /** Conversation history, persisted in Postgres by the JDBC chat-memory repository. */
  @Bean
  public ChatMemory chatMemory(
      ChatMemoryRepository repository,
      @Value("${specra.ai.chat-memory.max-messages:40}") int maxMessages) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(maxMessages)
        .build();
  }

  /**
   * Conversational client: remembers the thread identified by {@code ChatMemory.CONVERSATION_ID}.
   */
  @Bean
  @Primary
  public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
    return ChatClient.builder(chatModel)
        .defaultSystem(systemPrompt)
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor())
        .build();
  }

  /**
   * RAG client: deliberately has no memory advisor. Each question is answered only from the chunks
   * retrieved for it, so earlier turns cannot leak in and be mistaken for retrieved evidence.
   */
  @Bean
  public ChatClient ragChatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem(
            """
            Answer strictly from the supplied context. If the context does not contain the answer, \
            say you do not know rather than guessing. Cite the note titles you relied on.
            """)
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
  }
}
