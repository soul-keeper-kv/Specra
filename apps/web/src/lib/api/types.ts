/**
 * Mirrors the Spring Boot contract. Regenerate the authoritative version from the
 * running API with `pnpm gen:api` (writes src/types/api.d.ts) and diff against this
 * file when the backend changes.
 */

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

/**
 * RFC 9457 problem document — what every non-2xx response carries.
 *
 * `title` and `detail` are translated per request, so they are for display only. Anything the code
 * needs to decide on goes through `code`, which is stable across languages. See the `ApiProblem`
 * record on the API for the authoritative list.
 */
export type ApiProblem = {
  /** Stable URI for this class of error, e.g. https://specra.dev/problems/resource-not-found */
  type: string;
  title: string;
  status: number;
  detail: string;
  /** The request path that failed. */
  instance?: string;
  /** Machine-readable code, e.g. "validation-failed". Branch on this, never on the text. */
  code: string;
  timestamp: string;
  traceId?: string;
  requestId?: string;
  /** Present only when `code` is "validation-failed". Keyed by field name. */
  fieldErrors?: Record<string, string>;
};

export type ChatReply = {
  content: string;
  conversationId: string;
  provider: string;
  model: string | null;
};

export type Source = {
  title: string;
  /** Id of the document the chunk came from — a test case id today. */
  sourceId: string;
  excerpt: string;
  score: number | null;
};

export type AskReply = {
  answer: string;
  sources: Source[];
  provider: string;
  model: string | null;
};

export type ProviderInfo = {
  chatProvider: string;
  embeddingProvider: string;
  chatModelType: string;
  embeddingModelType: string;
  embeddingDimensions: number;
  availableProviders: string[];
};

// ── M1: tenancy, projects, test cases ─────────────────────────────────────────

export type Workspace = {
  id: string;
  name: string;
  slug: string;
  /** What the signed-in user is in this workspace. Every workspace a request can see is one
   * they belong to, so the UI can gate its actions straight off the listing. */
  role: WorkspaceRole;
  createdAt: string;
  updatedAt: string;
};

export type WorkspaceInput = {
  name: string;
  slug?: string;
};

export type AutomationEngine = "PLAYWRIGHT";

export type Project = {
  id: string;
  workspaceId: string;
  /** Short human handle, e.g. "ACME"; immutable once created. */
  key: string;
  name: string;
  description: string | null;
  engine: AutomationEngine;
  createdAt: string;
  updatedAt: string;
};

export type ProjectInput = {
  name: string;
  /** Left out, the API derives it from the name. */
  key?: string;
  description?: string;
  engine?: AutomationEngine;
};

export type ProjectPatch = {
  name?: string;
  description?: string;
};

export type ProjectQuery = {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export type TestCasePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type AutomationStatus = "NOT_AUTOMATED" | "MODELLED" | "GENERATED" | "COMMITTED";

export type TestCaseStep = {
  position: number;
  action: string;
  expected: string | null;
};

export type TestCase = {
  id: string;
  projectId: string;
  /** e.g. "TC-104" — minted by the API, stable for the life of the case. */
  reference: string;
  title: string;
  description: string | null;
  preconditions: string | null;
  expectedResult: string | null;
  priority: TestCasePriority;
  automationStatus: AutomationStatus;
  /** True when the case was edited after its IR was generated. */
  outOfDate: boolean;
  steps: TestCaseStep[];
  tags: string[];
  indexedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TestCaseSummary = {
  id: string;
  projectId: string;
  reference: string;
  title: string;
  priority: TestCasePriority;
  automationStatus: AutomationStatus;
  outOfDate: boolean;
  tags: string[];
  updatedAt: string;
};

export type TestCaseStepInput = {
  action: string;
  expected?: string;
};

export type TestCaseInput = {
  title: string;
  description?: string;
  preconditions?: string;
  expectedResult?: string;
  priority?: TestCasePriority;
  steps: TestCaseStepInput[];
  tags: string[];
};

export type TestCaseQuery = {
  q?: string;
  status?: AutomationStatus;
  tag?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export type AiAccount = {
  workspaceId: string;
  provider: string;
  chatModel: string | null;
  embeddingModel: string | null;
  monthlyBudgetUsd: number | null;
  /** Whether a key is stored; the key itself never crosses the wire outward. */
  keySet: boolean;
  updatedAt: string;
};

export type AiAccountInput = {
  provider: string;
  /** Omitted keeps the stored key, "" clears it, a value replaces it. */
  apiKey?: string;
  chatModel?: string;
  embeddingModel?: string;
  monthlyBudgetUsd?: number;
};

// ── accounts, sessions and roles ──────────────────────────────────────────────

export type AccountStatus = "ACTIVE" | "SUSPENDED";

export type Account = {
  id: string;
  email: string;
  displayName: string;
  status: AccountStatus;
  /** Preferred language tag, or null to follow the browser. */
  locale: string | null;
  lastLoginAt: string | null;
  createdAt: string;
};

/**
 * What register, login, refresh and change-password all return.
 *
 * `accessToken` goes on every request as `Authorization: Bearer …` and lives about fifteen
 * minutes. `refreshToken` is opaque, is only ever sent to `/api/v1/auth/refresh`, and is
 * invalidated by that call — the response carries its replacement.
 */
export type AuthTokens = {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  refreshToken: string;
  refreshExpiresAt: string;
  user: Account;
};

export type LoginInput = {
  email: string;
  password: string;
};

export type RegisterInput = {
  email: string;
  displayName: string;
  password: string;
};

export type ChangePasswordInput = {
  currentPassword: string;
  newPassword: string;
};

export type ProfileInput = {
  displayName: string;
  locale?: string | null;
};

/** One signed-in device. `current` marks the one making the request. */
export type AuthSession = {
  id: string;
  userAgent: string | null;
  clientIp: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  current: boolean;
};

/** Declared most senior first, matching the API — the order the roles are offered in. */
export type WorkspaceRole = "OWNER" | "ADMIN" | "MEMBER";

/**
 * A permission slug, e.g. `member-add`. The closed set lives on the API and is served by
 * `GET /api/v1/roles`; the UI matches on these strings rather than re-deciding what a role means.
 */
export type Permission =
  | "workspace-view"
  | "workspace-update"
  | "workspace-delete"
  | "member-view"
  | "member-add"
  | "member-update-role"
  | "member-remove"
  | "content-view"
  | "content-edit"
  | "content-delete";

export type RoleInfo = {
  role: WorkspaceRole;
  permissions: Permission[];
};

export type Member = {
  /** Id of the membership. The user is addressed by `userId` in every endpoint. */
  id: string;
  userId: string;
  email: string;
  displayName: string;
  role: WorkspaceRole;
  createdAt: string;
};

export type MemberAddInput = {
  email: string;
  role: WorkspaceRole;
};

// ── M2: Git ───────────────────────────────────────────────────────────────────

export type GitProviderKind = "GITHUB" | "GITLAB" | "BITBUCKET";

export type GitRepository = {
  projectId: string;
  provider: GitProviderKind;
  remoteUrl: string;
  defaultBranch: string;
  /** The branch operations act on; null means the default branch. */
  activeBranch: string | null;
  credentialId: string | null;
  connectedAt: string;
  updatedAt: string;
};

export type GitRepositoryInput = {
  provider: GitProviderKind;
  remoteUrl: string;
  defaultBranch: string;
  credentialId?: string;
};

export type GitVerifyResult = {
  defaultBranch: string;
  branches: string[];
};

export type GitChangeKind = "ADDED" | "MODIFIED" | "DELETED" | "UNTRACKED" | "CONFLICTING";

export type GitChange = {
  path: string;
  kind: GitChangeKind;
};

export type GitStatus = {
  branch: string;
  /** Commits a push would publish. */
  ahead: number;
  /** Commits a pull would fetch. */
  behind: number;
  clean: boolean;
  changes: GitChange[];
};

export type GitCommitInput = {
  message: string;
  paths: string[];
};

export type GitCommit = {
  sha: string;
  message: string;
};

export type GitCommitInfo = {
  sha: string;
  message: string;
  authorName: string;
  authorEmail: string;
  committedAt: string;
};

export type GitFileContent = {
  path: string;
  content: string;
};

export type GitBranchInput = {
  name: string;
  /** Where the branch starts; omitted means the repository's default branch. */
  from?: string;
};

export type GitCredential = {
  id: string;
  name: string;
  username: string | null;
  /** Whether a token is stored; the token itself never crosses the wire outward. */
  tokenSet: boolean;
  updatedAt: string;
};

export type GitCredentialInput = {
  name: string;
  username?: string;
  /** Write-only; encrypted at rest and never returned. */
  token: string;
};
