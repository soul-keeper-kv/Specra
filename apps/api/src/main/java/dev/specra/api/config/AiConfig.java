package dev.specra.api.config;

import dev.specra.api.feature.ai.tool.ContentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
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
@Configuration(proxyBeanMethods = false)
public class AiConfig {

  private final SpecraProperties.Ai properties;

  public AiConfig(SpecraProperties properties) {
    this.properties = properties.ai();
  }

  /** Conversation history, persisted in Postgres by the JDBC chat-memory repository. */
  @Bean
  public ChatMemory chatMemory(ChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(properties.chatMemory().maxMessages())
        .build();
  }

  /**
   * Conversational client: remembers the thread identified by {@code ChatMemory.CONVERSATION_ID}.
   */
  @Bean
  @Primary
  public ChatClient chatClient(
      ChatModel chatModel, ChatMemory chatMemory, ContentTools contentTools) {
    return ChatClient.builder(chatModel)
        .defaultSystem(properties.systemPrompt())
        .defaultTools(contentTools)
        .defaultAdvisors(
            MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor())
        .build();
  }

  /**
   * Modelling client: no memory, no tools, and the temperature pinned low. It answers one
   * structured question — "what does this test case intend" — and the answer is validated against a
   * schema, so the last thing wanted is variety. Options are the vendor-neutral kind; whichever
   * provider is active reads them.
   */
  @Bean
  public ChatClient modellingChatClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultOptions(ChatOptions.builder().temperature(0.1).build())
        .defaultAdvisors(new SimpleLoggerAdvisor())
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
            say you do not know rather than guessing. Cite the titles of the documents you relied on. \
            Answer in the same language the question was asked in.
            """)
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
  }
}
