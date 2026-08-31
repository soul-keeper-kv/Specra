package dev.specra.api.support;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** Echoes the prompt back so tests can assert on wiring without spending tokens. */
public class StubChatModel implements ChatModel {

  public static final String PREFIX = "stub:";

  /**
   * Deliberately awkward tokens: one starts with a space and one contains a newline. Those are the
   * two shapes plain SSE framing corrupts, so the streaming test only proves anything if they are
   * here.
   */
  public static final List<String> STREAM_TOKENS = List.of("Hello", " world", "\nsecond line");

  @Override
  public ChatResponse call(Prompt prompt) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(PREFIX + lastText(prompt)))));
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.fromIterable(STREAM_TOKENS)
        .map(token -> new ChatResponse(List.of(new Generation(new AssistantMessage(token)))));
  }

  private static String lastText(Prompt prompt) {
    var instructions = prompt.getInstructions();
    return instructions.isEmpty() ? "" : instructions.get(instructions.size() - 1).getText();
  }
}
