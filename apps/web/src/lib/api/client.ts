import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";

import {
  endSession,
  getSession,
  isAccessTokenExpired,
  isRefreshTokenExpired,
  sessionFromTokens,
  setSession,
} from "./session";
import type { ApiProblem, AuthTokens } from "./types";

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

/** Header the API reads, sanitises, echoes back, and stamps onto every log line of the request. */
export const REQUEST_ID_HEADER = "X-Request-Id";

export const REFRESH_PATH = "/api/v1/auth/refresh";

/** The API's code for "this token no longer verifies", which is the one worth a retry. */
const INVALID_TOKEN = "invalid-token";

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

/** Marks the one retry a request is allowed after its access token was refreshed. */
type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

export const api = axios.create({
  baseURL: API_URL,
  headers: { "Content-Type": "application/json" },
});

/**
 * Stamps the credential, the language and the correlation id onto every request.
 *
 * They are attached here rather than at the call sites so a hook cannot forget one: a missing
 * `Accept-Language` shows a Vietnamese user an English validation message, and a missing request
 * id makes an error report unsearchable in the API log.
 */
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // An access token that has already expired earns a refresh here rather than a 401 the user
  // waits through. The refresh call itself is exempt: it carries no access token, and asking it
  // to refresh before it can refresh is a loop.
  if (config.url !== REFRESH_PATH) await ensureFreshSession();

  const token = getSession()?.accessToken;
  if (token) config.headers.set("Authorization", `Bearer ${token}`);

  const locale = documentLocale();
  if (locale) config.headers.set("Accept-Language", locale);

  if (!config.headers.has(REQUEST_ID_HEADER)) {
    config.headers.set(REQUEST_ID_HEADER, newRequestId());
  }
  return config;
});

/**
 * Turns every failure into an `ApiError`, refreshing the access token once when that is what the
 * failure means.
 *
 * The retry is what stops a fifteen-minute token from being visible to the user. A 401 carrying
 * `invalid-token` means the credential was presented and did not verify, so exchanging the refresh
 * token and repeating the call is the right move; a 401 carrying anything else means there was no
 * credential at all, and retrying would loop. `_retried` bounds it to a single attempt.
 */
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) throw error;

    const config = error.config as RetriableConfig | undefined;
    const problem = problemOf(error);

    const refreshable =
      config !== undefined &&
      !config._retried &&
      problem?.code === INVALID_TOKEN &&
      config.url !== REFRESH_PATH &&
      Boolean(getSession()?.refreshToken);

    if (refreshable && (await refreshSession())) {
      config._retried = true;
      return api.request(config);
    }

    throw toApiError(error, problem);
  },
);

function problemOf(error: AxiosError): ApiProblem | undefined {
  const data = error.response?.data;
  return data !== null && typeof data === "object" ? (data as ApiProblem) : undefined;
}

function toApiError(error: AxiosError, problem?: ApiProblem): ApiError {
  const response = error.response;
  const requestId = headerOf(response, REQUEST_ID_HEADER) ?? requestIdOf(error.config);

  if (!response) {
    // No status at all: DNS, a refused connection, CORS, or an abort.
    return new ApiError(
      0,
      `Cannot reach the API at ${API_URL}. Is it running? (cd apps/api && ./mvnw spring-boot:run)`,
      undefined,
      requestId,
      error,
    );
  }

  return new ApiError(
    response.status,
    problem?.detail ?? problem?.title ?? `${response.status} ${response.statusText}`,
    problem,
    requestId,
    error,
  );
}

function headerOf(response: AxiosResponse | undefined, name: string): string | undefined {
  const value: unknown = response?.headers?.[name.toLowerCase()];
  return typeof value === "string" ? value : undefined;
}

function requestIdOf(config: AxiosRequestConfig | undefined): string | undefined {
  const value: unknown = config?.headers?.[REQUEST_ID_HEADER];
  return typeof value === "string" ? value : undefined;
}

/**
 * In flight, if a refresh is already running.
 *
 * Single-flight is not an optimisation here, it is correctness: the refresh token is rotated by the
 * call that uses it, so two concurrent queries each exchanging the same one would have the second
 * present a token the server has already retired — which it reads, correctly, as a replay, and
 * answers by signing every device out.
 */
let refreshing: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  refreshing ??= exchangeRefreshToken().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

/**
 * Brings the access token up to date before a call goes out, whatever transport carries it.
 *
 * The axios request interceptor calls this, and so does the SSE stream — which is the reason it is
 * a function rather than a few lines inside the interceptor. A token that has already expired is
 * exchanged here instead of being sent and refused, and a refresh token that is spent too ends the
 * session rather than buying a round trip that cannot succeed.
 *
 * Returns whether the caller now holds a usable access token. A signed-out caller gets `false`
 * without a request: there is nothing to refresh, and the endpoints that need no credential still
 * work.
 */
export async function ensureFreshSession(): Promise<boolean> {
  if (getSession() === null) return false;
  if (!isAccessTokenExpired()) return true;
  return renewSession();
}

/**
 * Exchanges the refresh token now, whatever the clock says.
 *
 * This is the other half of what the axios response interceptor does, for the transport that has
 * no interceptor: when the *server* refuses an access token the browser still believes in — a
 * revoked token, or a clock that ran ahead — asking `ensureFreshSession` would answer "it is
 * fine" and send the same dead credential again.
 */
export async function renewSession(): Promise<boolean> {
  if (getSession()?.refreshToken === undefined) return false;
  if (isRefreshTokenExpired()) {
    endSession("expired");
    return false;
  }
  return refreshSession();
}

async function exchangeRefreshToken(): Promise<boolean> {
  const refreshToken = getSession()?.refreshToken;
  if (!refreshToken) return false;

  try {
    // A bare axios call, not the instance: the exchange must not re-enter the response
    // interceptor that triggered it.
    const { data } = await axios.post<AuthTokens>(
      `${API_URL}${REFRESH_PATH}`,
      { refreshToken },
      { headers: { "Content-Type": "application/json", [REQUEST_ID_HEADER]: newRequestId() } },
    );
    setSession(sessionFromTokens(data));
    return true;
  } catch {
    // Expired, revoked, or replayed. Ending the session is all this layer does; the redirect
    // belongs to the route guard, which knows whether the current page even needs an account.
    // The distinction is for the user: a refresh token that was still in date and was refused
    // anyway means the server retired it — a password change, an administrator, or replay
    // detection — and that is worth wording differently from simply having been away too long.
    endSession(isRefreshTokenExpired() ? "expired" : "revoked");
    return false;
  }
}

/**
 * The verbs, already unwrapped.
 *
 * Hooks want the body, not an `AxiosResponse`, and they pass React Query's `signal` so a query
 * whose component has gone stops occupying a connection. Keeping the unwrap here means no call
 * site has to remember `.then((r) => r.data)`.
 */
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) => api.get<T>(url, config).then(unwrap),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    api.post<T>(url, body, config).then(unwrap),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    api.put<T>(url, body, config).then(unwrap),
  patch: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    api.patch<T>(url, body, config).then(unwrap),
  delete: <T = void>(url: string, config?: AxiosRequestConfig) =>
    api.delete<T>(url, config).then(unwrap),
};

function unwrap<T>(response: AxiosResponse<T>): T {
  return response.data;
}

/**
 * Headers for the one request that cannot go through axios.
 *
 * The SSE stream in `features/chat/api/ai.ts` reads tokens as they arrive, and the browser
 * adapters buffer a whole body before resolving — so that call stays on `fetch` and a
 * `ReadableStream`. It still has to carry the same credential and language, which is what this
 * hands it — including the refresh an expired token needs, which is why this is async. Reading the
 * token straight out of storage would send a spent credential and get a 401 the stream has no
 * interceptor to recover from.
 */
export async function streamHeaders(): Promise<Record<string, string>> {
  await ensureFreshSession();

  const token = getSession()?.accessToken;
  const locale = documentLocale();
  return {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
    [REQUEST_ID_HEADER]: newRequestId(),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(locale ? { "Accept-Language": locale } : {}),
  };
}

/**
 * Tells the API which language to answer in, so a validation message or an error detail comes back
 * already translated instead of being re-translated here from a code.
 *
 * The locale is read off `<html lang>`, which the locale layout sets — that avoids threading a
 * locale argument through every hook, and it cannot drift from what the user is looking at. On the
 * server there is no document, and the header is simply omitted.
 */
function documentLocale(): string | undefined {
  if (typeof document === "undefined") return undefined;
  return document.documentElement.lang || undefined;
}

/**
 * Generated here rather than on the server so the id exists even when the request never arrives —
 * a failed call can still be reported with something to search for.
 */
function newRequestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
