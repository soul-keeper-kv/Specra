package dev.specra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specra.api.support.StubChatModel;
import dev.specra.api.support.TestAiConfiguration;
import dev.specra.api.support.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Locks down the SSE wire format.
 *
 * <p>Tokens are JSON-encoded on purpose: a receiver must strip one space after {@code data:}, and a
 * newline inside a token would otherwise split it across frames. Both would silently corrupt model
 * output, so this asserts on the raw bytes rather than on a parsed convenience type.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class AiStreamIT {

  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper json;

  @LocalServerPort int port;

  @Test
  void streamsEachTokenAsAJsonStringAndEndsWithADoneEvent() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

    ResponseEntity<String> response =
        rest.exchange(
            "http://localhost:" + port + "/api/ai/chat/stream",
            HttpMethod.POST,
            new HttpEntity<>("{\"message\":\"hi\",\"conversationId\":\"stream-it\"}", headers),
            String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    String body = response.getBody();
    assertThat(body).isNotNull();

    // The awkward tokens must appear quoted and escaped, not raw.
    assertThat(body).contains("data:\" world\"");
    assertThat(body).contains("data:\"\\nsecond line\"");
    assertThat(body).contains("event:done");

    // And decoding the stream the way the web client does must rebuild the text exactly.
    assertThat(decodeTokens(body)).isEqualTo(String.join("", StubChatModel.STREAM_TOKENS));
  }

  /** Mirrors apps/web/src/lib/api/ai.ts: read data lines, JSON.parse each one. */
  private String decodeTokens(String body) throws Exception {
    List<String> tokens = new ArrayList<>();
    String event = null;
    for (String line : body.split("\\r?\\n")) {
      if (line.startsWith("event:")) {
        event = line.substring("event:".length()).trim();
      } else if (line.startsWith("data:") && "token".equals(event)) {
        String data = line.substring("data:".length());
        if (data.startsWith(" ")) {
          data = data.substring(1); // the one space a receiver must strip
        }
        tokens.add(json.readValue(data, String.class));
      }
    }
    return String.join("", tokens);
  }
}
