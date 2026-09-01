# 07 — Git

## The principle, stated as a test

**If Specra disappeared tomorrow, every user still has a working automation project.**

Concretely: clone their repository, `pnpm install`, `npx playwright test`, and the suite runs
with no reference to Specra. No proprietary runner, no API call at test time, no format only
we can read. Any design that fails this test is wrong, however convenient it is.

That is also the answer to "why not just store the code in Postgres": because then the
product owns the user's work, and the SDET who has to maintain it in five years cannot.

## The generated project

```text
<repo>/
├── tests/
│   └── auth/login.spec.ts
├── pages/
│   ├── LoginPage.ts
│   └── DashboardPage.ts
├── fixtures/
│   └── auth.fixture.ts
├── flows/
│   └── login.flow.ts            reusable IR flows, projected once
├── utils/
├── playwright.config.ts
├── package.json
└── .specra/
    ├── project.json             engine, adapter version, conventions
    └── models/TC-104.json       the IR, committed next to the code it produced
```

`.specra/` is committed on purpose. It makes the repository self-describing: another
checkout, another Specra workspace, or a plain `git clone` by a developer all carry the
intent alongside the code. It is metadata the user can read and delete; nothing at test time
depends on it.

The project layout is Page Object Model because that is what makes a locator change one edit
instead of twenty, and because it is what an automation engineer expects to find.

## Working copies

The API keeps a working copy per `(project, branch)` on disk. It is a **cache**, not the
truth:

- It can be deleted at any time and re-cloned.
- Nothing in the database points into it by path except transiently.
- A generation writes into it; a commit publishes it; a push shares it.
- If it is dirty when a generation wants to write, the API refuses with
  `WORKING_COPY_DIRTY` rather than clobbering a person's edits.

## The port

```java
interface GitProvider {
  String kind();                    // "github"
  void clone(RepoRef ref, Path into);
  void pull(Path copy);
  BranchList branches(RepoRef ref);
  void createBranch(Path copy, String name, String from);
  void checkout(Path copy, String branch);
  GitStatus status(Path copy);
  String diff(Path copy, String path);
  CommitResult commit(Path copy, String message, List<String> paths, Author author);
  void push(Path copy, String branch);
  List<CommitInfo> history(Path copy, String path, Pageable page);
}
```

Selected by configuration, discovered from the context, exactly like `ContentStore`: no
`if (provider.equals("github"))` anywhere. MVP ships the GitHub implementation; GitLab and
Bitbucket are the reason the interface exists.

Local mechanics are JGit. The provider abstraction is about **authentication, remotes and
hosted features** (PRs, checks), not about `git commit` — which is the same everywhere.

## Commits

- Specra commits **as the user**, with their name and email as author, and itself as
  committer. Attribution belongs to the person who approved the change.
- One approved proposal is one commit. A commit that bundles three unrelated fixes is not
  reviewable, and reviewability is the point.
- Messages follow Conventional Commits, generated from the change:
  `test(auth): generate login spec from TC-104`.
- `.specra/models/TC-104.json` changes in the same commit as the code it produced. The IR and
  its projection never drift in history.

## Push failures are a user's problem to resolve

A rejected non-fast-forward push surfaces as `GIT_PUSH_REJECTED` with the option to pull. It
is never resolved by force-pushing, and never by a background rebase the user did not ask
for. Their repository, their history.

## Credentials

A credential is stored encrypted, referenced by id from `git_repositories`, and never
returned by the API. GitHub App installation tokens are preferred over PATs once we have the
app; the port is written so that swapping is a configuration change.
