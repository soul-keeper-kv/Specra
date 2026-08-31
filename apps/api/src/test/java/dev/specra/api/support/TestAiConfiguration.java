package dev.specra.api.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Tests run with {@code spring.ai.model.chat=none} and {@code spring.ai.model.embedding=none}, so
 * no vendor auto-configuration fires and these two beans are the only models in the context. The
 * rest of the AI stack — ChatClient, pgvector VectorStore, advisors — is the production wiring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfiguration {

  @Bean
  ChatModel chatModel() {
    return new StubChatModel();
  }

  @Bean
  EmbeddingModel embeddingModel() {
    return new HashingEmbeddingModel();
  }
}
