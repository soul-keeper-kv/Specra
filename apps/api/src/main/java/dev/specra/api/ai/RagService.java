package dev.specra.api.ai;

import dev.specra.api.ai.dto.AskReply;
import dev.specra.api.ai.dto.AskRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Retrieval-augmented generation over pgvector.
 *
 * <p>Text goes in as one logical source, is split into chunks, embedded by whichever embedding
 * model is active, and stored. Questions retrieve the closest chunks and hand them to the chat model
 * as grounding context.
 */
@Service
public class RagService {

  /** Metadata key that ties every chunk back to the row it came from. */
  public static final String SOURCE_ID = "sourceId";

  private static final Logger log = LoggerFactory.getLogger(RagService.class);
  private static final int EXCERPT_CHARS = 320;

  private final VectorStore vectorStore;
  private final ChatClient ragChatClient;
  private final TokenTextSplitter splitter;
  private final String chatProvider;

  public RagService(
      VectorStore vectorStore,
      @Qualifier("ragChatClient") ChatClient ragChatClient,
      @Value("${spring.ai.model.chat}") String chatProvider,
      @Value("${specra.ai.rag.chunk-size:800}") int chunkSize) {
    this.vectorStore = vectorStore;
    this.ragChatClient = ragChatClient;
    this.chatProvider = chatProvider;
    this.splitter =
        TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(chunkSize / 4)
            .withKeepSeparator(true)
            .build();
  }

  /**
   * Re-index one source. Old chunks are removed first so editing a note cannot leave stale text
   * retrievable.
   */
  public int replaceDocument(String sourceId, String text, Map<String, Object> metadata) {
    deleteBySource(sourceId);

    Map<String, Object> meta = new HashMap<>(metadata);
    meta.put(SOURCE_ID, sourceId);

    List<Document> chunks =
        splitter.apply(List.of(Document.builder().text(text).metadata(meta).build()));
    if (chunks.isEmpty()) {
      return 0;
    }
    vectorStore.add(chunks);
    log.info("Indexed source {} as {} chunk(s)", sourceId, chunks.size());
    return chunks.size();
  }

  public void deleteBySource(String sourceId) {
    // sourceId is always a UUID we generated, so it cannot break out of the filter literal.
    vectorStore.delete(SOURCE_ID + " == '" + sourceId + "'");
  }

  public AskReply ask(AskRequest request) {
    SearchRequest search =
        SearchRequest.builder()
            .query(request.question())
            .topK(request.topKOrDefault())
            .similarityThreshold(request.thresholdOrDefault())
            .build();

    ChatClientResponse response =
        ragChatClient
            .prompt()
            .advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(search).build())
            .user(request.question())
            .call()
            .chatClientResponse();

    return new AskReply(
        text(response), sources(response), chatProvider, model(response));
  }

  /** Raw similarity search, no generation — useful for debugging what RAG actually retrieves. */
  public List<AskReply.Source> retrieve(String query, int topK, double threshold) {
    List<Document> docs =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build());
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
              String.valueOf(d.getMetadata().getOrDefault(SOURCE_ID, "")),
              body.length() > EXCERPT_CHARS ? body.substring(0, EXCERPT_CHARS) + "…" : body,
              d.getScore()));
    }
    return out;
  }
}
