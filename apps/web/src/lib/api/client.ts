import type { ApiProblem } from "./types";

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

/** Header the API reads, sanitises, echoes back, and stamps onto every log line of the request. */
export const REQUEST_ID_HEADER = "X-Request-Id";

/**
 * Carries the API's RFC 9457 problem document.
 *
 * `code` is what the UI should branch on — `message` is translated by the server and will differ
 * between languages. `requestId` is worth showing to the user: it is the string that finds this
 * exact request in the API log.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  readonly requestId?: string;
  readonly traceId?: string;
  readonly problem?: ApiProblem;

  constructor(
    status: number,
    message: string,
    problem?: ApiProblem,
    requestId?: string,
    cause?: unknown,
  ) {
    super(message, { cause });
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.code = problem?.code ?? (status === 0 ? "network-error" : "unknown");
    this.fieldErrors = problem?.fieldErrors ?? {};
    this.requestId = problem?.requestId ?? requestId;
    this.traceId = problem?.traceId;
  }

  /** True when the API is unreachable rather than returning an error status. */
  get isNetworkError() {
    return this.status === 0;
  }
}

type RequestOptions = Omit<RequestInit, "body"> & { body?: unknown };

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options;
  const requestId = newRequestId();

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...rest,
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...localeHeader(),
        [REQUEST_ID_HEADER]: requestId,
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    throw new ApiError(
      0,
      `Cannot reach the API at ${API_URL}. Is it running? (cd apps/api && ./mvnw spring-boot:run)`,
      undefined,
      requestId,
      cause,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const parsed = text ? safeJson(text) : undefined;

  if (!response.ok) {
    const problem = parsed as ApiProblem | undefined;
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `${response.status} ${response.statusText}`,
      problem,
      response.headers.get(REQUEST_ID_HEADER) ?? requestId,
    );
  }

  return parsed as T;
}

/**
 * Tells the API which language to answer in, so a validation message or an error detail comes back
 * already translated instead of being re-translated here from a code.
 *
 * The locale is read off `<html lang>`, which the locale layout sets — that avoids threading a
 * locale argument through every hook, and it cannot drift from what the user is looking at. On the
 * server there is no document, and the header is simply omitted.
 */
function localeHeader(): Record<string, string> {
  if (typeof document === "undefined") return {};
  const lang = document.documentElement.lang;
  return lang ? { "Accept-Language": lang } : {};
}

/**
 * Generated here rather than on the server so the id exists even when the request never arrives —
 * a failed fetch can still be reported with something to search for.
 */
function newRequestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export function buildQuery(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}
