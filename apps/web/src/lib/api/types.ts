/**
 * Mirrors the Spring Boot contract. Regenerate the authoritative version from the
 * running API with `pnpm gen:api` (writes src/types/api.d.ts) and diff against this
 * file when the backend changes.
 */

export type Note = {
  id: string;
  title: string;
  content: string;
  tags: string[];
  /** Set once the note's chunks are in pgvector; null means the index is stale. */
  indexedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type NoteInput = {
  title: string;
  content: string;
  tags: string[];
};

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
  noteId: string;
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

export type NoteQuery = {
  q?: string;
  tag?: string;
  page?: number;
  size?: number;
  sort?: string;
};
