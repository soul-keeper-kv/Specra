package dev.specra.api.feature.ai.service;

import dev.specra.api.config.SpecraProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * The write side of the vector store: text in, chunks stored.
 *
 * <p>Split out of {@link RagService} so a feature that only needs to keep its rows indexed — the
 * note feature does — depends on chunking and storage alone, and not on the chat client, the
 * retrieval advisor or anything else answering a question needs.
 */
@Service
public class DocumentIndexService {

  /** Metadata key that ties every chunk back to the row it came from. */
  public static final String SOURCE_ID = "sourceId";

  private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

  private final VectorStore vectorStore;
  private final TokenTextSplitter splitter;

  public DocumentIndexService(VectorStore vectorStore, SpecraProperties properties) {
    this.vectorStore = vectorStore;
    int chunkSize = properties.ai().rag().chunkSize();
    this.splitter =
        TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(chunkSize / 4)
            .withKeepSeparator(true)
            .build();
  }

  /**
   * Re-index one source. Old chunks are removed first so editing a row cannot leave stale text
   * retrievable.
   *
   * @return how many chunks the text was stored as
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
}
