# specra-api

Spring Boot 3.5 · Java 17 · Maven.

## Stack

| Area | What is used |
| --- | --- |
| Web | spring-boot-starter-web, validation, actuator |
| Data | Spring Data JPA, PostgreSQL, Flyway (`flyway-database-postgresql`) |
| AI | Spring AI 1.1.8 — starters for anthropic / openai / ollama / transformers |
| RAG | `spring-ai-starter-vector-store-pgvector`, `spring-ai-advisors-vector-store`, markdown & tika document readers |
| Chat memory | `spring-ai-starter-model-chat-memory-repository-jdbc` (stored in Postgres) |
| Docs | springdoc-openapi 2.9 → `/swagger-ui.html` |
| Codegen | Lombok, MapStruct 1.6 |
| Dev | devtools, docker-compose support, configuration-processor |
| Tests | JUnit 5, Testcontainers (real pgvector), failsafe |

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

## Endpoints

| | |
| --- | --- |
| `GET /api/notes?q=&tag=&page=&size=&sort=` | Paged list |
| `GET POST PUT DELETE /api/notes[/{id}]` | CRUD |
| `POST /api/notes/{id}/index` | chunk → embed → pgvector |
| `GET /api/ai/providers` | Active chat and embedding providers |
| `POST /api/ai/chat` | Chat, with history |
| `POST /api/ai/chat/stream` | Same, streaming tokens over SSE |
| `DELETE /api/ai/chat/{conversationId}` | Forget one conversation |
| `POST /api/ai/ask` | RAG |
| `GET /api/ai/retrieve?q=&topK=&threshold=` | Similarity search only, for debugging |

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
npm run db:reset      # from the repo root
```

## Tests

`NoteServiceTest` is a plain unit test, and it uses the real MapStruct-generated
`NoteMapperImpl` so mapping bugs surface there.

`NoteApiIT` and `AiStreamIT` run against real PostgreSQL + pgvector through
Testcontainers. The models are replaced by
[StubChatModel](src/test/java/dev/specra/api/support/StubChatModel.java) and
[HashingEmbeddingModel](src/test/java/dev/specra/api/support/HashingEmbeddingModel.java) —
the latter hashes words into buckets and normalises, so texts sharing vocabulary really
do land close together and similarity-search assertions actually mean something.
