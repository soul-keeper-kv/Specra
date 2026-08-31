---
name: specra-ai
description: Work on Specra's AI layer — Spring AI, switching or adding an LLM provider, ChatClient, advisors, chat memory, pgvector RAG, embeddings, chunking, and SSE token streaming. Use when touching apps/api/src/main/java/dev/specra/api/ai/, AiConfig, spring.ai.* configuration, or the streaming/chat/RAG code in apps/web.
---

# Specra's AI layer

Spring AI 1.1.8. The rule that runs through all of it: **no vendor is ever named in
code**.

## How provider selection works

All four starters sit on the classpath (`anthropic`, `openai`, `ollama`,
`transformers`), but Spring AI only instantiates the one selected by:

```yaml
spring.ai.model.chat: ${AI_CHAT_PROVIDER:anthropic}
spring.ai.model.embedding: ${AI_EMBEDDING_PROVIDER:transformers}
```

So the context holds **exactly one** `ChatModel` bean and one `EmbeddingModel`, and
`AiConfig` injects them by type.

### Do not

```java
// WRONG
@Qualifier("anthropicChatModel") ChatModel model;
if ("openai".equals(provider)) { ... }
new AnthropicChatModel(api, options);
```

### Do

```java
// The only ChatModel in the context; which vendor it is is a config concern
public AiConfig(ChatModel chatModel) { ... }
```

To report what is running, read the `spring.ai.model.chat` property (that is what
`AiController` does to build `ProviderInfo`) — never infer it from a class name.

The valid values live in `org.springframework.ai.model.SpringAIModels`.

### Anthropic has no embedding model

That is why `AI_EMBEDDING_PROVIDER` defaults to `transformers` (local ONNX, 384
dimensions, no API key). Do not "fix" it to match the chat provider.

Changing the embedding provider changes the vector width, so the table has to go:

```bash
npm run db:reset
```

## Two ChatClients, deliberately different

| Bean | Advisors | Used by |
| --- | --- | --- |
| `chatClient` (`@Primary`) | `MessageChatMemoryAdvisor` + logger | `/api/ai/chat`, `/chat/stream` |
| `ragChatClient` | logger only — **no memory** | `RagService.ask` |

RAG has no memory on purpose: with it, earlier turns bleed into the retrieved context
and the model is liable to treat them as evidence. Do not merge the two beans.

Pass the conversation id through an advisor param:

```java
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
```

## The SSE streaming contract — the easiest thing to break

`AiController.stream()` sends every token as a **JSON string**:

```java
.map(token -> ServerSentEvent.builder(asJson(token)).event("token").build())
```

It looks redundant. It is not. Two reasons:

1. The SSE spec requires a receiver to **strip one space** after `data:`. Model tokens
   very often begin with a space (`" world"`), so `"Hello world"` arrives as
   `"Helloworld"`.
2. A token containing a newline would be split across several `data:` lines, i.e.
   several frames.

The client in `apps/web/src/lib/api/ai.ts` calls `JSON.parse` to recover the exact text.

Change one side and you **must** change the other. `AiStreamIT` asserts on the real
bytes over HTTP, and `StubChatModel.STREAM_TOKENS` deliberately contains both a
space-leading token and one with a newline — drop the JSON encoding and that test fails.

The web client parses frames itself with `fetch` + `ReadableStream` rather than using
`EventSource`, because `EventSource` cannot POST.

## RAG

`RagService`:

- `replaceDocument(sourceId, text, metadata)` — deletes old chunks by `sourceId`
  **first**, then chunks, embeds and stores. That is what stops an edited note leaving
  stale text retrievable.
- `deleteBySource(sourceId)` — filters on `sourceId == '<uuid>'`
- `ask(request)` — `QuestionAnswerAdvisor`, and returns `sources` pulled from
  `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` in `ChatClientResponse.context()`
- `retrieve(...)` — similarity search only, no generation; use it to see what RAG
  actually pulls back

The metadata key tying a chunk to its source row is the constant `RagService.SOURCE_ID`.

Chunking uses `TokenTextSplitter` (its builder methods carry a `with…` prefix:
`withChunkSize`, `withMinChunkSizeChars`, `withKeepSeparator`).

## Quick checks

```bash
curl -s localhost:8080/api/ai/providers            # which provider is live
curl -s 'localhost:8080/api/ai/retrieve?q=...&threshold=0.1'   # what RAG retrieves
```

With no API key the app **still starts normally** and only returns 502 on a call
(`GlobalExceptionHandler` catches `NonTransientAiException`). That is by design, not a
bug.

## Check the Spring AI API before writing against it

Spring AI changes its API between minor versions. Do not write from memory — read the
jar:

```bash
J=$(find ~/.m2/repository/org/springframework/ai/spring-ai-client-chat -name "*.jar" ! -name "*sources*" | head -1)
javap -cp "$J" org.springframework.ai.chat.client.ChatClient
```
