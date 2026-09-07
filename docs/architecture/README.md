# Specra architecture

Specra is an **AI test automation IDE**: it turns a manual QA test case into an executable
automation test, while Git stays the source of truth for the code and Playwright stays the
execution engine.

These documents are the decisions, not a tutorial. They exist because the product is easy to
mistake for "a CRUD app with a Generate button", and it is not — the shape that matters is:

```text
Requirement / Manual test case
        │
        ▼
   AI understanding
        │
        ▼
   Test Model / IR          ← the abstraction the whole product turns on
        │
        ├──────────────► test planning
        ▼
  Playwright adapter        ← the only engine-specific code in the repo
        │
        ▼
   Source code  ──►  Git    ← source of truth, user-owned
        │
        ▼
   Playwright runner
        │
        ▼
   Result + evidence
        │
        ▼
   AI failure analysis
        │
        ▼
   Fix proposal  ──►  human approves  ──►  commit
```

| Document                                          | Answers                                                              |
| ------------------------------------------------- | -------------------------------------------------------------------- |
| [00 — Product](00-product.md)                     | What we build, for whom, and what we refuse to build                 |
| [01 — Domain model](01-domain-model.md)           | The entities, their relationships, and their state machines          |
| [02 — Test Model / IR](02-test-model-ir.md)       | The IR contract: actions, targets, versioning, who owns the schema   |
| [03 — Module boundaries](03-module-boundaries.md) | Which runtime owns what, and which direction imports may point       |
| [04 — Database](04-database.md)                   | Tables, tenancy, what the database is _not_ allowed to own           |
| [05 — API contracts](05-api-contracts.md)         | The REST surface behind the golden path                              |
| [06 — Execution](06-execution.md)                 | The runner service, run lifecycle, artifacts, isolation              |
| [07 — Git](07-git.md)                             | The `GitProvider` port, working copies, the generated project layout |
| [08 — AI pipeline](08-ai-pipeline.md)             | The five AI roles, their contracts, and what AI may never do         |
| [09 — Roadmap](09-roadmap.md)                     | Milestone order, and what current scaffold gets deleted when         |
| [10 — Test management](10-test-management.md)     | Jira/Xray connections, import ownership, Basic/JQL search and limits |

Read [00](00-product.md) and [02](02-test-model-ir.md) before writing code. Everything else
is reference you come back to.
