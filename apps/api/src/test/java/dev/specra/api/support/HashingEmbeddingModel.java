package dev.specra.api.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic, offline stand-in for a real embedding model.
 *
 * <p>Hashes each word into a bucket and L2-normalises, so texts that share vocabulary really do
 * land close together under cosine distance. That makes similarity search assertions meaningful
 * without calling out to a provider or downloading an ONNX model in CI.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSIONS = 384;

  @Override
  public float[] embed(Document document) {
    return embed(document.getText() == null ? "" : document.getText());
  }

  @Override
  public float[] embed(String text) {
    float[] v = new float[DIMENSIONS];
    for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
      if (token.isEmpty()) {
        continue;
      }
      int bucket = Math.floorMod(token.hashCode(), DIMENSIONS);
      v[bucket] += 1f;
    }
    double norm = 0;
    for (float f : v) {
      norm += f * f;
    }
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (int i = 0; i < v.length; i++) {
        v[i] /= (float) norm;
      }
    } else {
      v[0] = 1f; // never hand pgvector an all-zero vector
    }
    return v;
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<Embedding> out = new ArrayList<>();
    List<String> inputs = request.getInstructions();
    for (int i = 0; i < inputs.size(); i++) {
      out.add(new Embedding(embed(inputs.get(i)), i));
    }
    return new EmbeddingResponse(out);
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }
}
