---
name: specra-feature
description: "Build or change a feature in Specra end to end, applying every cross-cutting concern the project already standardised on — i18n on both sides, RFC 9457 errors, tracing, OpenAPI, light/dark, sidebar and command-palette registration, tests. Use whenever adding or changing a page, a route, an endpoint, an entity, a form, a dialog, an error case, or any other user-visible surface, in either apps/web or apps/api. Read this before writing the first file, not after."
---

# Adding a feature to Specra

The stack is decided. This skill exists so nobody re-derives it, and so the four or five
things that are easy to forget get done every time.

## Decisions already settled — do not ask, do not re-litigate

| Topic          | Decision                                                                   | Lives in                             |
| -------------- | -------------------------------------------------------------------------- | ------------------------------------ |
| Languages      | Vietnamese (default) + English, everywhere, both apps                      | `i18n/routing.ts`, `SupportedLocale` |
| URL shape      | `/vi/…` · `/en/…`, prefix always present, browser language detected on `/` | `i18n/routing.ts`, `proxy.ts`        |
| Theme          | light · dark · system, next-themes, class on `<html>`                      | `components/providers.tsx`, `theme/` |
| UI kit         | shadcn/ui, preset `radix-nova`, lucide icons                               | `components.json`, `components/ui/`  |
| Error format   | RFC 9457 problem+json, one factory, stable `code`, translated text         | `core/error/ProblemFactory`          |
| Correlation    | `X-Request-Id` in, echoed out, `traceId`/`spanId` in MDC and in the body   | `core/logging/`, Micrometer Tracing  |
| API docs       | springdoc, one document at `/v3/api-docs`, error responses added globally  | `config/OpenApiConfig`               |
| Code structure | `core` + `feature/` (api), `features/` + thin pages (web)                  | `specra-architecture`                |
| Auth           | JWT bearer + rotating refresh token; every `/api/**` call needs one        | `feature/auth`, `lib/api/session.ts` |
| Roles          | per workspace — OWNER · ADMIN · MEMBER, on a `workspace_members` row       | `WorkspaceRole`, `WorkspaceAccess`   |

If a request seems to need one of these changed, say so and keep building under the
existing decision — do not open a question about it.

## Non-negotiables, every time

For Jira/Xray connections, external test search or import, also use `specra-testmanagement`.
Its [blueprint](../../../docs/architecture/10-test-management.md) records the provider and
Basic/JQL contracts; the integration feeds the golden path without becoming an issue tracker.

These are what get forgotten. Treat the list as the definition of "the feature is built":

1. **No literal user-facing string.** Every label, placeholder, toast, empty state, aria
   label, error sentence, page title. Web → `src/messages/en.json` **and** `vi.json`.
   API → `i18n/messages.properties` **and** `messages_vi.properties`.
2. **No ad-hoc error body.** Throw a `BusinessException`; the handler renders it.
3. **No `next/link` or `next/navigation` for links and routing.** Use `@/i18n/navigation`.
4. **No `toLocaleString()`.** Use `useFormatter()`.
5. **No `setState` inside `useEffect`.** It is a lint error, not a warning.
6. **A new page is registered**, or it exists but nobody can reach it (see below).
7. **The colours come from tokens** (`bg-card`, `text-muted-foreground`, `border`), never
   `bg-white` / `text-black` — that is the whole of dark mode working.

## API side — the vertical slice

Reference: `feature/testcase/`. Details of JPA/MapStruct/Flyway are in the `specra-api` skill;
this is the order and the parts that cross concerns.

1. `resources/db/migration/V<n>__<name>.sql` — Hibernate runs `ddl-auto: validate`, so an
   entity without a migration fails startup.
2. `feature/<name>/` — one folder per layer: `domain/` (entity + repository), `mapper/`,
   `service/`, `web/` (controller), `dto/`.
   Lists return `PageResponse.from(page, mapper::toResponse)`.
3. **Validation messages as keys**, never literals:
   ```java
   @NotBlank(message = "{validation.project.name.required}")
   @Size(max = 120, message = "{validation.project.name.size}")
   ```
   then the key into both bundles. `{max}`, `{min}`, `{value}` interpolate from the
   annotation.
4. **Errors.** Reuse an `ErrorCode` if one fits; a 404 is one line:
   ```java
   throw new ResourceNotFoundException("resource.project", id);   // + resource.project in both bundles
   ```
   A genuinely new class of failure means: a constant in `ErrorCode`, then
   `error.<slug>.title` and `error.<slug>.detail` in both bundles, then either a
   `BusinessException` subclass or an `@ExceptionHandler` in `GlobalExceptionHandler`.
5. **OpenAPI.** `@Tag` on the controller, `@Operation(summary = …)` on each method,
   `@Schema(example = …)` on DTO fields. The `Accept-Language` header and the 400/404/409/
   502/500 problem responses are added to every operation by `OpenApiConfig` — do not repeat
   them.
6. **Config goes in `SpecraProperties`**, not a loose `@Value`. It is bound and validated at
   startup, so a typo fails the boot instead of surfacing later.
7. **Tests.** `*Test` for units (surefire), `*IT` for Testcontainers (failsafe). A new error
   case belongs in `ProblemResponseIT`, asserted in **both** languages — an English-only
   assertion passes even when the bundle has stopped being wired in.

## Web side — the vertical slice

Reference: `features/testcases/`.

1. `features/<name>/` — `api/` (Query hooks + a `<name>Keys` object), `components/`,
   `schemas.ts` if it has a form. Hooks call `http.*` from `lib/api/client.ts` and nothing
   else — the axios instance, the interceptors and the hook shape are the `specra-web-api`
   skill.
2. `app/[locale]/(app)/<name>/page.tsx` stays thin:
   ```tsx
   export default async function ProjectsPage({ params }: PageProps<"/[locale]/projects">) {
     const { locale } = await params;
     setRequestLocale(locale);
     return <ProjectsView />;
   }
   ```
   `setRequestLocale` is not optional — without it the segment silently drops out of static
   rendering. Add `generateMetadata` with `getTranslations({ locale, namespace })` so the tab
   title is translated too.
3. **Register the page** — a new entry in `WORKSPACE_NAV` (`lib/config/navigation.ts`) puts
   it in the sidebar, the command palette and the breadcrumb trail at once. `labelKey` is a
   **union type**: widen it, and add the matching `nav.<key>` to both bundles, or it will not
   compile.
4. **Schemas take a translator**, never module scope:
   ```ts
   const schema = useMemo(() => buildProjectSchema(tValidation), [tValidation]);
   ```
5. **States, not just the happy path.** Loading → `Skeleton`, empty → `EmptyState` with a
   next step, failure → `ErrorState` (it already surfaces the `traceId`). A bare empty table
   reads as a bug.
6. **Server errors into the form**:
   ```tsx
   serverErrors={mutation.error instanceof ApiError ? mutation.error.fieldErrors : undefined}
   ```
   Branch on `error.code`, never on `error.message` — the message is translated per request.
7. **Destructive actions get an `AlertDialog`.** Anything that also drops embeddings or
   cannot be undone.
8. **Tests.** Vitest for logic; add to `tests/e2e/specs/smoke.spec.ts` — a separate package,
   not part of apps/web — only what holds with the API down, and import expected text from
   `@messages/*.json` rather than typing it again (that alias is the only path e2e has into
   the web app's source).

## Adding a language

`SupportedLocale` + `messages_xx.properties` (api) · `routing.ts` + `LOCALE_LABELS` +
`messages/xx.json` + the `proxy.ts` matcher (web). Both parity tests then fail until the new
bundle covers every key — that is the point.

## Definition of done

Run them; do not assume. The i18n parity tests and the lint rules are the ones that catch
what this skill is about.

```bash
cd apps/api && ./mvnw -B verify   # add `clean` if a resource assertion fails on a file you can see is right
pnpm test:runner && pnpm test:model
cd apps/web && pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

Then, if the change is user-visible, `pnpm test:e2e` (builds and serves on :3100 itself).

Look at the result in **both languages and both themes** before calling it finished:
`/vi/…`, `/en/…`, and the theme toggle. A missing key renders as the raw key path, and a
hard-coded colour only shows up in dark mode.
