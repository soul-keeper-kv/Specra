# specra-runner

The toolchain plane ([03 — Module boundaries](../../docs/architecture/03-module-boundaries.md)).
It exists for one reason: Playwright and the TypeScript toolchain are Node-only, and generated
code has to be typechecked with the real compiler rather than hoped about.

```text
apps/api  ──HTTP, job-shaped──►  services/runner
                                   codegen · inspect · execute
```

Stateless: no database, nothing on disk that outlives a job. Everything arrives in the job
payload and leaves in the result.

## What is here today

| Folder                     | State                                                                            |
| -------------------------- | -------------------------------------------------------------------------------- |
| `src/adapters/playwright/` | The IR → TypeScript projection. **The only folder that may name an engine.**     |
| `src/codegen/`             | Project scaffolding and file assembly over the adapter                           |
| `src/inspect/`             | Not built yet — M8                                                               |
| `src/execute/`             | Not built yet — M6                                                               |
| `src/server/`              | Not built yet — M6; `apps/api` calls the codegen path in-process-free until then |

## The two rules that hold this package up

1. **`(IR, page objects, options) → files` is a pure function.** No model call, no clock, no
   random, no filesystem read inside `generate()`. The golden files in
   `src/codegen/__golden__/` are the test: same IR in, identical bytes out, on every machine.
2. **Engine vocabulary is contained.** `page`, `locator`, `getByRole`, `expect` appear only
   under `src/adapters/playwright/`. `containment.test.ts` fails the build on a leak, the same
   way `ArchitectureTest` does for LLM vendors in Java.

## The generated project

What `generate()` emits is a standalone Playwright project
([07 — Git](../../docs/architecture/07-git.md)): `tests/`, `pages/`, `flows/`, `fixtures/`,
`playwright.config.ts`, `package.json`, and a committed `.specra/` that describes itself.

The acceptance test is portability: clone the user's repository, `pnpm install`,
`npx playwright test` — and it runs with no reference to Specra.

```bash
pnpm --filter specra-runner test        # vitest, including the golden files
pnpm --filter specra-runner typecheck
```
