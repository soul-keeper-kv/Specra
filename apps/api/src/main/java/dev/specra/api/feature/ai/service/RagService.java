package dev.specra.api.feature.ai.service;

import dev.specra.api.feature.ai.dto.AskReply;
import dev.specra.api.feature.ai.dto.AskRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The read side of retrieval-augmented generation: a question retrieves the closest chunks from
 * pgvector and hands them to the chat model as grounding context.
 *
 * <p>Getting text <em>into</em> the store is {@link DocumentIndexService}'s job.
 */
@Service
public class RagService {

  private static final int EXCERPT_CHARS = 320;

  private final VectorStore vectorStore;
  private final ChatClient ragChatClient;
  private final AiProviders providers;
  private final AiFailures failures;

  public RagService(
      VectorStore vectorStore,
      @Qualifier("ragChatClient") ChatClient ragChatClient,
      AiProviders providers,
      AiFailures failures) {
    this.vectorStore = vectorStore;
    this.ragChatClient = ragChatClient;
    this.providers = providers;
    this.failures = failures;
  }

  public AskReply ask(AskRequest request) {
    SearchRequest search =
        SearchRequest.builder()
            .query(request.question())
            .topK(request.topKOrDefault())
            .similarityThreshold(request.thresholdOrDefault())
            .build();

    ChatClientResponse response =
        failures.guard(
            () ->
                ragChatClient
                    .prompt()
                    .advisors(
                        QuestionAnswerAdvisor.builder(vectorStore).searchRequest(search).build())
                    .user(request.question())
                    .call()
                    .chatClientResponse());

    return new AskReply(text(response), sources(response), providers.chat(), model(response));
  }

  /** Raw similarity search, no generation — useful for debugging what RAG actually retrieves. */
  public List<AskReply.Source> retrieve(String query, int topK, double threshold) {
    List<Document> docs =
        vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK).similarityThreshold(threshold).build());
    return toSources(docs == null ? List.of() : docs);
  }

  private static String text(ChatClientResponse response) {
    var chatResponse = response.chatResponse();
    if (chatResponse == null || chatResponse.getResult() == null) {
      return "";
    }
    return chatResponse.getResult().getOutput().getText();
  }

  private static String model(ChatClientResponse response) {
    var chatResponse = response.chatResponse();
    return chatResponse == null ? null : chatResponse.getMetadata().getModel();
  }

  @SuppressWarnings("unchecked")
  private static List<AskReply.Source> sources(ChatClientResponse response) {
    Object retrieved = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
    if (retrieved instanceof List<?> list) {
      return toSources((List<Document>) list);
    }
    return List.of();
  }

  private static List<AskReply.Source> toSources(List<Document> docs) {
    List<AskReply.Source> out = new ArrayList<>(docs.size());
    for (Document d : docs) {
      String body = d.getText() == null ? "" : d.getText();
      out.add(
          new AskReply.Source(
              String.valueOf(d.getMetadata().getOrDefault("title", "Untitled")),
              String.valueOf(d.getMetadata().getOrDefault(DocumentIndexService.SOURCE_ID, "")),
              body.length() > EXCERPT_CHARS ? body.substring(0, EXCERPT_CHARS) + "…" : body,
              d.getScore()));
    }
    return out;
  }
}
