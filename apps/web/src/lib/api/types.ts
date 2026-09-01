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
