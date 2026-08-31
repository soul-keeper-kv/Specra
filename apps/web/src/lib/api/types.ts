/**
 * Mirrors the Spring Boot contract. Regenerate the authoritative version from the
 * running API with `npm run gen:api` (writes src/types/api.d.ts) and diff against this
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

export type ApiErrorBody = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: Record<string, string>;
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
