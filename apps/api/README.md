# specra-api

Spring Boot 3.5 · Java 17 · Maven.

## Stack

| Area          | What is used                                                                                        |
| ------------- | --------------------------------------------------------------------------------------------------- |
| Web           | spring-boot-starter-web, validation, actuator, aop                                                  |
| Data          | Spring Data JPA, PostgreSQL, Flyway (`flyway-database-postgresql`)                                   |
| AI            | Spring AI 1.1.8 — starters for anthropic / openai / ollama / transformers                           |
| RAG           | `spring-ai-starter-vector-store-pgvector`, `spring-ai-advisors-vector-store`, markdown & tika readers |
| Chat memory   | `spring-ai-starter-model-chat-memory-repository-jdbc` (stored in Postgres)                          |
| Observability | Micrometer Tracing (OTel bridge), Prometheus registry, MDC correlation                              |
| i18n          | `MessageSource` over `i18n/messages*.properties`, `Accept-Language` per request                     |
| Docs          | springdoc-openapi 2.9 → `/swagger-ui.html`                                                          |
| Codegen       | Lombok, MapStruct 1.6                                                                               |
| Dev           | devtools, docker-compose support, configuration-processor                                           |
| Tests         | JUnit 5, Testcontainers (real pgvector), failsafe                                                   |

> Spring AI 1.1.8 is built against Spring Boot 3.5.15, so it pairs with the 3.5.x line.
> Spring Initializr now only serves Boot 4.x, which is why this `pom.xml` is
> hand-written.

## Running

```bash
./mvnw spring-boot:run            # uses the Postgres in compose.yaml (started for you)
./mvnw spring-boot:test-run       # uses a disposable pgvector container
./mvnw test                       # unit tests only, no Docker needed
./mvnw verify                     # adds the *IT classes — needs Docker
./mvnw package                    # jar
```

## Package layout

A `core` layer that knows nothing about the domain, and one package per feature owning its
whole vertical slice.

```text
dev.specra.api
├── config/            @Configuration only
│   ├── SpecraProperties     everything under specra.*, bound and validated at startup
│   ├── AiConfig             the two ChatClients — no vendor named
│   ├── I18nConfig           LocaleResolver + validator bound to the MessageSource
│   ├── WebMvcConfig         CORS, including the exposed correlation headers
│   ├── ObservabilityConfig  @Observed/@Timed aspects, the access-log filter
│   └── OpenApiConfig        info, tags, and the error responses on every operation
├── core/
│   ├── error/         ErrorCode · BusinessException · ProblemFactory · GlobalExceptionHandler
│   ├── i18n/          SupportedLocale · MessageResolver · LocalizedText · HttpLocaleResolver
│   ├── logging/       MdcKeys · CorrelationIdFilter · RequestLoggingFilter
│   └── web/           PageResponse
└── feature/
    ├── note/          Note · repository · service · controller · mapper · dto/
    └── ai/            AiController · RagService · dto/
```

## Endpoints

|                                            |                                                  |
| ------------------------------------------ | ------------------------------------------------ |
| `GET /api/notes?q=&tag=&page=&size=&sort=` | Paged list                                       |
| `GET POST PUT DELETE /api/notes[/{id}]`    | CRUD                                             |
| `POST /api/notes/{id}/index`               | chunk → embed → pgvector                         |
| `GET /api/ai/providers`                    | Active chat and embedding providers              |
| `POST /api/ai/chat`                        | Chat, with history                               |
| `POST /api/ai/chat/stream`                 | Same, streaming tokens over SSE                  |
| `DELETE /api/ai/chat/{conversationId}`     | Forget one conversation                          |
| `POST /api/ai/ask`                         | RAG                                              |
| `GET /api/ai/retrieve?q=&topK=&threshold=` | Similarity search only, for debugging            |
| `GET /actuator/{health,info,metrics,prometheus,loggers}` | Ops                                 |

## Errors

Every non-2xx response is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem
document (`application/problem+json`), built only by `ProblemFactory`:

```json
{
  "type": "https://specra.dev/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "detail": "Note 7f3c1b2e-… does not exist.",
  "instance": "/api/notes/7f3c1b2e",
  "code": "resource-not-found",
  "timestamp": "2026-01-01T00:00:00Z",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "requestId": "8f14e45f-ceea-467a-a3c9"
}
```

`code` is the contract. `title` and `detail` are translated per request and will differ
between callers, so nothing should branch on them. Validation failures add
`fieldErrors`, keyed by field name.

To raise one, throw — never build a response:

```java
throw new ResourceNotFoundException("resource.note", id);   // 404, translated
throw new ConflictException("error.conflict.detail");       // 409
```

`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so Spring MVC's own
failures — unreadable JSON, wrong method, unknown path, an unconvertible parameter — come
back in the same shape rather than as an HTML error page.

## Internationalisation

```bash
curl localhost:8080/api/notes/00000000-0000-0000-0000-000000000000 -H 'Accept-Language: vi'
curl 'localhost:8080/api/notes/00000000-0000-0000-0000-000000000000?lang=vi'
```

Bundles live in `src/main/resources/i18n/`: `messages.properties` is the English fallback,
`messages_vi.properties` the Vietnamese one. Adding a language means adding a bundle and a
constant in `SupportedLocale`.

Bean Validation messages resolve from the same bundle, because `I18nConfig` replaces Boot's
validator with one bound to the `MessageSource` — so `@NotBlank(message =
"{validation.note.title.required}")` is translated too. `MessageBundleTest` fails the build
if a key exists in one bundle and not the other, or if an apostrophe in a parameterised
message is left undoubled (MessageFormat would swallow the rest of the sentence).

## Tracing and logs

Micrometer Tracing gives every request a span; `CorrelationIdFilter` adds a `requestId`,
accepted from `X-Request-Id` when the caller supplies one and sanitised before it is used.
Both ids are echoed as response headers, repeated in the error body, and printed on every
log line:

```text
2026-01-01T00:00:00.000Z  INFO [specra-api,0af76519…,b9c7…,8f14e45f…] … : GET /api/notes -> 200 in 12ms
```

`RequestLoggingFilter` writes that one access line per request — never the body, which
would put user text and prompts into the log and would break the SSE endpoint. Anything
slower than `specra.logging.access.slow-request-millis` is logged at WARN.

Under the `prod` profile the console switches to ECS JSON (`logging.structured.format.console`),
so those MDC fields arrive in Elasticsearch or Loki as queryable fields. No span exporter is
configured: the ids are useful on their own, and shipping them somewhere is one dependency
plus `management.otlp.tracing.endpoint`.

## Switching provider

```properties
spring.ai.model.chat=anthropic|openai|ollama
spring.ai.model.embedding=transformers|openai|ollama
```

All four starters are on the classpath; those two properties decide which one
auto-configures, so the context holds exactly one `ChatModel` and one `EmbeddingModel`.
[AiConfig](src/main/java/dev/specra/api/config/AiConfig.java) injects them by type — no
vendor is named anywhere in the code.

Anthropic provides no embedding model, which is why the embedding default is
`transformers` (local ONNX, 384 dimensions, no key required).

## Schema

Flyway owns the business tables
([V1__init.sql](src/main/resources/db/migration/V1__init.sql)); Hibernate runs
`ddl-auto: validate` to catch mapping drift.

The `vector_store` table is **not** under Flyway: its vector width depends on the active
embedding model, so Spring AI creates it (`initialize-schema: true`) and switching
providers needs no new migration. Switching the embedding provider does mean dropping
that table first:

```bash
pnpm db:reset      # from the repo root
```

## Tests

7 unit + 17 integration.

`NoteServiceTest` is a plain unit test, and it uses the real MapStruct-generated
`NoteMapperImpl` so mapping bugs surface there. `MessageBundleTest` guards the translation
bundles without booting Spring.

`NoteApiIT`, `ProblemResponseIT` and `AiStreamIT` run against real PostgreSQL + pgvector
through Testcontainers. The models are replaced by
[StubChatModel](src/test/java/dev/specra/api/support/StubChatModel.java) and
[HashingEmbeddingModel](src/test/java/dev/specra/api/support/HashingEmbeddingModel.java) —
the latter hashes words into buckets and normalises, so texts sharing vocabulary really
do land close together and similarity-search assertions actually mean something.

`ProblemResponseIT` asserts the error contract from the outside, in both languages: if the
Vietnamese assertions ever start passing with English strings, the message bundle has
silently stopped being wired in.
