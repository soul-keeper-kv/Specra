---
name: specra-api
description: "Work on Specra's Spring Boot backend in apps/api — adding or changing JPA entities, repositories, services, controllers, DTOs, MapStruct mappers, Flyway migrations, bean validation, RFC 9457 error handling, i18n message bundles, request tracing, or JUnit/Testcontainers tests. Use whenever touching Java, pom.xml, application.yml, or this project's PostgreSQL schema."
---

# Specra backend (apps/api)

Spring Boot 3.5.16 · Java 17 · Maven · JPA/PostgreSQL · Flyway · MapStruct · Lombok.

Adding or changing something a user sees? Read `specra-feature` first — it is the
end-to-end checklist, and it points back here for the JPA/Flyway/MapStruct detail.

The AI/RAG/streaming layer has its own skill, `specra-ai` — read that instead when
touching `feature/ai/` or `AiConfig`.

Wondering which folder something belongs in, or why `ArchitectureTest` is red? That is
`specra-architecture`.

## Package layout

```text
dev.specra.api
├── config/       @Configuration only; SpecraProperties holds everything under specra.*
├── core/         no domain knowledge: error/ · i18n/ · logging/ · web/ · content/
└── feature/      one folder per domain, one folder per layer inside it
    ├── testcase/ web/ · service/ · domain/ · mapper/ · dto/    ← the reference slice
    ├── codegen/  the IR projected into files; a person applies them
    └── ai/       web/ · service/ · tool/ · dto/
```

A class that a second feature would need belongs in `core/`, not in the feature that
happened to need it first.

## Layering — plain, and enforced

`web/ → service/ → domain/`. Three layers, no ports and no adapters. **A class goes in the
folder its role names**, so where a file sits tells you what it is allowed to touch:

| Folder     | Holds                                               | May depend on               |
| ---------- | --------------------------------------------------- | --------------------------- |
| `web/`     | `@RestController`                                   | `service/`, `dto/`, `core/` |
| `service/` | services, `*Events`, `@Tool` classes, port adapters | `domain/`, `dto/`, `core/`  |
| `mapper/`  | MapStruct interfaces (service-layer collaborator)   | `domain/`, `dto/`           |
| `domain/`  | `@Entity`, Spring Data repositories                 | `core/` only                |
| `dto/`     | request/response records                            | nothing of its own feature  |

`src/test/java/dev/specra/api/ArchitectureTest.java` (ArchUnit, runs in `./mvnw test`, no
Docker) fails the build on:

| Rule                                                                                     | Why it exists                                                               |
| ---------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `core` depending on `feature`                                                            | core is shared plumbing; a class naming a feature belongs in it             |
| a cycle between features                                                                 | testcase → ai is fine; ai → testcase as well means neither can change alone |
| `service/` accessed by anything but `web/`, `domain/` by anything but `service/`         | that is the layering itself                                                 |
| a `@RestController` outside `web/`, an `@Entity` or repository outside `domain/`         | otherwise the rule above is trivial to dodge                                |
| anything depending on a `*Controller`                                                    | a controller is an entry point, not a collaborator                          |
| `service/`, `mapper/` or `domain/` seeing `jakarta.servlet` or `org.springframework.web` | a service is also called by tests and schedulers                            |
| importing `org.springframework.ai.<vendor>`                                              | the provider is `spring.ai.model.*`, chosen at runtime                      |
| `@Autowired` on a field                                                                  | constructor injection keeps the class buildable with `new`                  |

Practical consequences when writing a feature:

- **A handler delegates once.** Two service calls in one controller method is a use case
  without a home — `DELETE /api/v1/test-cases/{id}` calls `TestCaseService.delete` and nothing else.
- **A service returns a DTO**, never an entity. `NoteService.require` returns a `Note` and
  is deliberately package-private: it is for the other service in the same feature.
- **Cross-feature side effects go through an event.** `NoteService` publishes
  `NoteEvents.NoteDeleted` / `NoteContentChanged`; `NoteIndexService` listens with
  `@TransactionalEventListener(AFTER_COMMIT)` and drops the embeddings. CRUD therefore
  knows nothing about vectors, and nothing is dropped for a transaction that rolls back.
- **Do not wrap a model or network call in `@Transactional`.** `NoteIndexService.index` is
  intentionally un-annotated: embedding takes seconds and a transaction would hold a
  database connection for all of it. Read, call out, then mark the row in a short write.

### Trap: `default` methods on a repository interface

Mockito intercepts default methods too, so `@Mock NoteRepository` returns `null` from one
instead of running its body — the service then silently does the wrong thing and the unit
test still passes. Put "find it or throw" in the service (`NoteService.require`), not in
the repository.

## Version constraints — read before deciding to "upgrade"

`pom.xml` is **hand-written**, not generated by Initializr. The reason: Initializr no
longer serves Spring Boot 3.x (only 4.0.x/4.1.x), while Spring AI 1.1.8 is built against
**Boot 3.5.15**. The 3.5.16 + 1.1.8 pairing is verified.

Moving to Boot 4.x means moving to Spring AI 2.x as well. That is a decision, not
maintenance. Do not do it unless explicitly asked.

To check compatibility when a version does need to change:

```bash
curl -s https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-starter-model-anthropic/<ver>/spring-ai-starter-model-anthropic-<ver>.pom | grep -A1 spring-boot-starter
```

## Who owns which table

| Table                        | Created by                                          |
| ---------------------------- | --------------------------------------------------- |
| everything but the two below | Flyway — `src/main/resources/db/migration/`, V1…V11 |
| `vector_store`               | **Spring AI**, `initialize-schema: true`            |
| `SPRING_AI_CHAT_MEMORY`      | **Spring AI**, `initialize-schema: always`          |

Hibernate runs `ddl-auto: validate`, so any entity change **must** come with a new
migration (the next free `V<n>__…sql`; V11 is the latest), or the app fails at startup — that is
intentional.

Do not write a migration for `vector_store`: its vector width changes with the active
embedding model (transformers 384 / ollama 768 / openai 1536).

**A migration that has run anywhere is immutable — including its comments.** Flyway
checksums the whole file, so rewording a `--` line fails `flyway:validate` on every database
that already applied it, and the app refuses to start. Put the better wording in the Java that
reads the table. If one was edited by mistake: `./mvnw flyway:repair` with the datasource
properties rewrites the stored checksum and touches no data — on that one machine.

## `feature/codegen` — two verbs that must stay two endpoints

| Endpoint                                    | Does                                                                |
| ------------------------------------------- | ------------------------------------------------------------------- |
| `POST /api/v1/test-cases/{id}/code`         | projects the IR into files, stores a proposal — **writes nothing**  |
| `GET  /api/v1/test-cases/{id}/code`         | the proposal awaiting review, each file beside its current contents |
| `POST /api/v1/code-generations/{id}/apply`  | writes the files, commits exactly those paths as the user           |
| `POST /api/v1/code-generations/{id}/reject` | nothing is written                                                  |

`PROPOSED → APPLIED | REJECTED | SUPERSEDED`. **Never add an endpoint that generates and
applies**, however convenient — the human decision between them is the product, and a
convenience route makes it optional. Generating supersedes any older `PROPOSED` row, because
two live proposals offer a reviewer two futures for the same file.

## `ErrorCode` — declaration order is load-bearing

`forStatus` returns the **first** constant with a given status, so the general one must be
declared before the specific ones that share it. `RESOURCE_NOT_FOUND` before
`EXTERNAL_TEST_NOT_FOUND`; `CONFLICT` before `GENERATION_NOT_PROPOSED`;
`AI_PROVIDER_UNAVAILABLE` before `RUNNER_UNAVAILABLE`. Adding a constant in the wrong place
silently changes what an unhandled exception maps to — put it after the general one and say
in its javadoc why it sits there.

## Adding a domain — the vertical slice

Follow `feature/testcase/` as the reference. In order:

1. `src/main/resources/db/migration/V<n>__<name>.sql` — table plus indexes
2. `feature/<name>/domain/<Name>.java` — entity with `@Getter @Setter @NoArgsConstructor`,
   `@EntityListeners(AuditingEntityListener.class)`, `@Version` for optimistic locking
3. `feature/<name>/domain/<Name>Repository.java` — `JpaRepository<T, UUID>`, queries only
4. `feature/<name>/dto/<Name>Request.java` and `Response.java` — Java records, bean validation
   on the request, `@Schema` so OpenAPI picks it up
5. `feature/<name>/mapper/<Name>Mapper.java` — MapStruct, `componentModel = "spring"`
6. `feature/<name>/service/<Name>Service.java` — `@Transactional(readOnly = true)` on the
   class, `@Transactional` on write methods
7. `feature/<name>/web/<Name>Controller.java` — `/api/<plural>`, return `PageResponse<T>`
   for lists
8. Tests mirror the package: unit in `*Test.java` next to what they test, integration in
   `*IT.java` under `web/`

### MapStruct trap

An `@ElementCollection` **must** be `@Mapping(target = "...", ignore = true)` and then
updated through a mutate-in-place method on the entity (see `Note.replaceTags`). Letting
MapStruct assign a fresh collection makes Hibernate delete and reinsert every row.

Always ignore: `id`, `createdAt`, `updatedAt`, `version`.

`NoteServiceTest` uses the real `NoteMapperImpl` (the class MapStruct generates) rather
than a mock, so mapping bugs surface at the unit-test level.

## HTTP conventions

- Lists are always wrapped in `PageResponse<T>`, never Spring's own `Page<T>` (its shape
  is unstable and Spring warns about serialising it).
- Never catch an exception in a controller, and never build an error body by hand.
- Constrain query parameters the same way you constrain a body — `@Validated` on the
  controller plus `@Min`/`@Max`/`@NotBlank` with a bundle key, as `/api/ai/retrieve` does.
  An unbounded `topK` reaches the vector store exactly like a bounded one.

## Errors — RFC 9457, one factory

Every non-2xx response is an `application/problem+json` document produced by
`ProblemFactory` and rendered by `core/error/GlobalExceptionHandler`. On top of the
standard members it always carries `code` (the stable `ErrorCode` slug clients branch
on), `timestamp`, `traceId` and `requestId`; validation failures add `fieldErrors`.

To raise one, throw:

```java
throw new ResourceNotFoundException("resource.test-case", id);   // 404
throw new ConflictException("error.conflict.detail");       // 409
```

`BusinessException` carries a **message key**, not a message: the text is resolved at the
edge in the caller's locale, so a service never has to know which language it serves.

Adding an error case:

1. A constant in `ErrorCode` (status, `type` URI and message keys derive from the name).
2. `error.<slug>.title` and `error.<slug>.detail` in **both** bundles — `MessageBundleTest`
   fails otherwise.
3. Either a `BusinessException` subclass, or an `@ExceptionHandler` in
   `GlobalExceptionHandler` for a third-party exception.
4. `ApiProblem` if you added a new extension property — it is documentation-only, and the
   OpenAPI schema (and therefore the web app's generated types) comes from it.

The handler extends `ResponseEntityExceptionHandler`, so Spring MVC's own failures land
there too. Do not add a second `@RestControllerAdvice`.

## i18n

No user-facing string is written in Java. Bundles live in `src/main/resources/i18n/`:
`messages.properties` (English fallback) and `messages_vi.properties`.

- Resolve text through `MessageResolver`, never `MessageSource` directly.
- A message argument that is itself a noun goes in as `LocalizedText("resource.test-case")`, so
  the whole sentence ends up in one language.
- Bean Validation resolves from the same bundle — `I18nConfig` replaces Boot's validator —
  so write `@NotBlank(message = "{validation.testcase.title.required}")`, never a literal.
- `MessageBundleTest` fails the build on a key present in one bundle and missing from
  another, and on an undoubled apostrophe in a parameterised message (MessageFormat treats
  a single quote as an escape and swallows the rest of the sentence).

Locale comes from `Accept-Language`, and only from there — no `?lang=` parameter. For a quick
check with curl: `-H 'Accept-Language: vi'`.

## Logging and tracing

`CorrelationIdFilter` runs first and populates the MDC (`requestId`, plus method, path,
client IP, locale), then clears it in a `finally` — threads are pooled, and a leaked entry
would attribute one user's lines to another request. Micrometer Tracing adds `traceId` and
`spanId`. Both ids reach the client, in headers and in the error body.

`RequestLoggingFilter` writes one access line per request and never touches the body:
buffering it would break the SSE endpoint and put user text into the log.

Measuring a method needs no timer: annotate it `@Observed` (span + timer) or `@Timed`.

## Tests

```bash
./mvnw test      # unit only — no Docker needed
./mvnw verify    # adds the *IT classes — REQUIRES Docker
```

Failsafe runs `*IT`, surefire runs `*Test`. Follow that naming.

`ProblemResponseIT` pins the error contract from the outside, in both languages: if the
Vietnamese assertions ever start passing with English strings, the message bundle has
silently stopped being wired in.

Integration tests use a **real** PostgreSQL + pgvector through Testcontainers
(`TestcontainersConfiguration`). The AI models are replaced with stubs in `support/`,
and `application-test.yml` sets `spring.ai.model.chat=none` /
`embedding=none` so no vendor auto-configuration runs.

`HashingEmbeddingModel` hashes words into buckets and normalises, so texts sharing
vocabulary genuinely land close together — that is what makes similarity-search
assertions meaningful. Do not replace it with random vectors.

Run the app against a disposable database:

```bash
./mvnw spring-boot:test-run
```

## When the contract changes

Changing a DTO or an endpoint means updating:

- `apps/web/src/lib/api/types.ts` — the hand-written types the web app actually uses
- `apps/web/src/features/testcases/api/testcases.ts` and `apps/web/src/features/chat/api/ai.ts` — the
  TanStack Query hooks

Cross-check against the live schema (needs the API running):

```bash
cd apps/web && pnpm gen:api    # writes src/types/api.d.ts for comparison
```
