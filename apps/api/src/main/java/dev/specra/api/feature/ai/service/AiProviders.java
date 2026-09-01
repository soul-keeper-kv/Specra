package dev.specra.api.feature.ai.service;

import dev.specra.api.feature.ai.dto.ProviderInfo;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that reads which providers are active.
 *
 * <p>{@code spring.ai.model.*} used to be re-read with {@code @Value} in every class that wanted to
 * report the provider name. Reading it once here keeps that property name in one file and lets the
 * rest of the feature depend on a method instead of on a property string.
 *
 * <p>Still no vendor is named: these are configuration values, and {@link #info()} reports the
 * model beans Spring AI actually instantiated.
 */
@Component
public class AiProviders {

  /** Every provider whose starter this project puts on the classpath. */
  private static final List<String> BUNDLED =
      List.of("anthropic", "openai", "ollama", "transformers");

  private final String chat;
  private final String embedding;
  private final ChatModel chatModel;
  private final EmbeddingModel embeddingModel;

  public AiProviders(
      @Value("${spring.ai.model.chat}") String chat,
      @Value("${spring.ai.model.embedding}") String embedding,
      ChatModel chatModel,
      EmbeddingModel embeddingModel) {
    this.chat = chat;
    this.embedding = embedding;
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
  }

  /** Name of the active chat provider, as answers report it back to the caller. */
  public String chat() {
    return chat;
  }

  public String embedding() {
    return embedding;
  }

  public ProviderInfo info() {
    return new ProviderInfo(
        chat,
        embedding,
        chatModel.getClass().getSimpleName(),
        embeddingModel.getClass().getSimpleName(),
        embeddingModel.dimensions(),
        BUNDLED);
  }
}
