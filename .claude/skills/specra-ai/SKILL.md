---
name: specra-ai
description: Work on Specra's AI layer — Spring AI, switching or adding an LLM provider, ChatClient, advisors, chat memory, pgvector RAG, embeddings, chunking, and SSE token streaming. Use when touching apps/api/src/main/java/dev/specra/api/feature/ai/, AiConfig, spring.ai.* configuration, or the streaming/chat/RAG code in apps/web.
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
`AiProviders` does to build `ProviderInfo`) — never infer it from a class name. That class
is the only place the property is read; take the name from it rather than adding another
`@Value` somewhere new.

The valid values live in `org.springframework.ai.model.SpringAIModels`.

### Anthropic has no embedding model

That is why `AI_EMBEDDING_PROVIDER` defaults to `transformers` (local ONNX, 384
dimensions, no API key). Do not "fix" it to match the chat provider.

Changing the embedding provider changes the vector width, so the table has to go:

```bash
pnpm db:reset
```

## Two ChatClients, deliberately different

| Bean                      | Advisors                            | Used by          |
| ------------------------- | ----------------------------------- | ---------------- |
| `chatClient` (`@Primary`) | `MessageChatMemoryAdvisor` + logger | `ChatService`    |
| `ragChatClient`           | logger only — **no memory**         | `RagService.ask` |

RAG has no memory on purpose: with it, earlier turns bleed into the retrieved context
and the model is liable to treat them as evidence. Do not merge the two beans.

Pass the conversation id through an advisor param:

```java
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
```

## The SSE streaming contract — the easiest thing to break

`ChatService.streamTokens()` returns the model's tokens and nothing else; framing them is
the controller's job. `AiController.stream()` sends every token as a **JSON string**:

```java
.map(token -> ServerSentEvent.builder(asJson(token)).event("token").build())
```

It looks redundant. It is not. Two reasons:

1. The SSE spec requires a receiver to **strip one space** after `data:`. Model tokens
   very often begin with a space (`" world"`), so `"Hello world"` arrives as
   `"Helloworld"`.
2. A token containing a newline would be split across several `data:` lines, i.e.
   several frames.

The client in `apps/web/src/features/chat/api/ai.ts` calls `JSON.parse` to recover the exact
text.

Change one side and you **must** change the other. `AiStreamIT` asserts on the real
bytes over HTTP, and `StubChatModel.STREAM_TOKENS` deliberately contains both a
space-leading token and one with a newline — drop the JSON encoding and that test fails.

The web client parses frames itself with `fetch` + `ReadableStream` rather than using
`EventSource`, because `EventSource` cannot POST.

## RAG

Two classes, split by direction, so a feature that only keeps its rows indexed does not
depend on the chat client as well.

`DocumentIndexService` — text in:

- `replaceDocument(sourceId, text, metadata)` — deletes old chunks by `sourceId`
  **first**, then chunks, embeds and stores. That is what stops an edited note leaving
  stale text retrievable.
- `deleteBySource(sourceId)` — filters on `sourceId == '<uuid>'`; idempotent

`RagService` — questions out:

- `ask(request)` — `QuestionAnswerAdvisor`, and returns `sources` pulled from
  `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` in `ChatClientResponse.context()`
- `retrieve(...)` — similarity search only, no generation; use it to see what RAG
  actually pulls back

The metadata key tying a chunk to its source row is `DocumentIndexService.SOURCE_ID`.

Indexing is called from the owning feature, never the other way round: `NoteIndexService`
calls `replaceDocument`, and drops chunks on `NoteEvents.NoteDeleted` /
`NoteContentChanged` after the note's transaction commits.

Chunking uses `TokenTextSplitter` (its builder methods carry a `with…` prefix:
`withChunkSize`, `withMinChunkSizeChars`, `withKeepSeparator`).

## Is the provider actually up

`AiHealthService` sends one throwaway token to the active chat model every
`specra.ai.health.interval` and keeps the answer in memory. `GET /api/ai/health` serves
that cached reading — it never calls a provider on the request thread.

Four statuses, and they are the contract clients branch on: `UP`, `DOWN` (with the
`ErrorCode` slug in `code`), `NOT_CONFIGURED` (no key, so nothing was sent), `UNKNOWN` (no
probe has completed). Always HTTP 200 — this reports a state rather than failing in one.

- The probe is a real generation, not a TCP ping: a revoked key, an exhausted quota, a
  missing model and an Ollama daemon that never pulled its model all accept a connection
  happily. It asks for **one token**, and `AiHealthServiceTest` asserts that — the ceiling
  is what makes probing every five minutes free of consequence.
- It runs on its own executor under `specra.ai.health.timeout`, never on the scheduler
  thread. A provider that accepts the socket and then goes quiet must not take the
  application's other scheduled work down with it.
- `?refresh=true` probes on demand for a "test connection" button, debounced by
  `min-refresh-interval` because the endpoint has no auth and the call behind it is
  billable. `AI_HEALTH_ENABLED=false` stops only the timer; refresh keeps working.
- The reading is also a Micrometer gauge, `specra.ai.provider.up`, tagged by provider —
  the actuator's Prometheus endpoint is already exposed.

Durations everywhere here are ISO-8601 (`PT5M`), because `@Scheduled` reads `interval`
straight from the environment and parses no other form. `5m` binds fine and then fails at
startup.

## Quick checks

```bash
curl -s localhost:8080/api/ai/providers            # which provider is live
curl -s localhost:8080/api/ai/health               # last probe of the chat provider
curl -s 'localhost:8080/api/ai/health?refresh=true'            # probe now
curl -s 'localhost:8080/api/ai/retrieve?q=...&threshold=0.1'   # what RAG retrieves
```

With no API key the app **still starts normally** and only returns 502 on a call
(`GlobalExceptionHandler` maps `NonTransientAiException` to the `ai-provider-error` code,
and `TransientAiException` to `ai-provider-unavailable` — 503, because a retry may work).
That is by design, not a
bug.

## Check the Spring AI API before writing against it

Spring AI changes its API between minor versions. Do not write from memory — read the
jar:

```bash
J=$(find ~/.m2/repository/org/springframework/ai/spring-ai-client-chat -name "*.jar" ! -name "*sources*" | head -1)
javap -cp "$J" org.springframework.ai.chat.client.ChatClient
```
