import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { ApiError, REQUEST_ID_HEADER, api, http } from "./client";
import { clearSession, consumeSessionEndedReason, getSession, setSession } from "./session";
import type { Account } from "./types";

type Canned = { status: number; data?: unknown; headers?: Record<string, string> };

const requests: InternalAxiosRequestConfig[] = [];

/** Answers each call with the next canned response, the way a real adapter settles a status. */
function respondWith(...canned: Canned[]): void {
  const queue = [...canned];
  const adapter: AxiosAdapter = async (config) => {
    requests.push(config as InternalAxiosRequestConfig);
    const next = queue.shift() ?? { status: 500 };
    const response = {
      data: next.data,
      status: next.status,
      statusText: "",
      headers: next.headers ?? {},
      config,
    };
    if (next.status >= 200 && next.status < 300) return response;
    throw new AxiosError("failed", String(next.status), config, {}, response);
  };
  api.defaults.adapter = adapter;
  // The refresh exchange deliberately bypasses the instance, so it needs the same stand-in.
  axios.defaults.adapter = adapter;
}

const ACCOUNT: Account = {
  id: "u-1",
  email: "qa@specra.dev",
  displayName: "QA",
  status: "ACTIVE",
  locale: "vi",
  lastLoginAt: null,
  createdAt: "2026-01-01T00:00:00Z",
};

const tokens = (accessToken: string) => ({
  user: ACCOUNT,
  accessToken,
  expiresAt: "2999-01-01T00:00:00Z",
  refreshToken: "refresh-2",
  refreshExpiresAt: "2999-01-01T00:00:00Z",
});

/** Signed in, but the access token is already spent — the state an idle tab wakes up in. */
function signInExpired() {
  setSession({
    user: ACCOUNT,
    accessToken: "stale",
    expiresAt: "2020-01-01T00:00:00Z",
    refreshToken: "refresh-1",
    refreshExpiresAt: "2999-01-01T00:00:00Z",
  });
}

/** Both tokens spent: nothing left to refresh with. */
function signInDead() {
  setSession({
    user: ACCOUNT,
    accessToken: "stale",
    expiresAt: "2020-01-01T00:00:00Z",
    refreshToken: "refresh-1",
    refreshExpiresAt: "2020-01-02T00:00:00Z",
  });
}

function signIn(accessToken = "access-1") {
  setSession({
    user: ACCOUNT,
    accessToken,
    expiresAt: "2999-01-01T00:00:00Z",
    refreshToken: "refresh-1",
    refreshExpiresAt: "2999-01-01T00:00:00Z",
  });
}

beforeEach(() => {
  requests.length = 0;
  clearSession();
});

afterEach(() => {
  clearSession();
  delete api.defaults.adapter;
  delete axios.defaults.adapter;
});

describe("the axios instance", () => {
  it("stamps the token, the language and a request id onto every call", async () => {
    document.documentElement.lang = "vi";
    signIn();
    respondWith({ status: 200, data: ACCOUNT });

    await http.get<Account>("/api/v1/auth/me");

    const headers = requests[0].headers;
    expect(headers.get("Authorization")).toBe("Bearer access-1");
    expect(headers.get("Accept-Language")).toBe("vi");
    expect(headers.get(REQUEST_ID_HEADER)).toBeTruthy();
  });

  it("sends no Authorization header when nobody is signed in", async () => {
    respondWith({ status: 200, data: [] });

    await http.get("/api/v1/roles");

    expect(requests[0].headers.has("Authorization")).toBe(false);
  });

  it("unwraps the response body, so a hook never sees an AxiosResponse", async () => {
    respondWith({ status: 200, data: ACCOUNT });

    await expect(http.get<Account>("/api/v1/auth/me")).resolves.toEqual(ACCOUNT);
  });
});

describe("error mapping", () => {
  it("turns a problem document into an ApiError the UI can branch on", async () => {
    respondWith({
      status: 400,
      data: {
        title: "Validation failed",
        detail: "Kiểm tra lại các trường",
        code: "validation-failed",
        fieldErrors: { email: "must be an email" },
        requestId: "req-9",
      },
      headers: { "x-request-id": "req-9" },
    });

    const error = await http.post("/api/v1/auth/register", {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe("validation-failed");
    expect(apiError.fieldErrors).toEqual({ email: "must be an email" });
    expect(apiError.requestId).toBe("req-9");
  });

  it("reports an unreachable API as status 0 rather than an HTTP failure", async () => {
    api.defaults.adapter = () => Promise.reject(new AxiosError("Network Error"));

    const error = (await http.get("/api/v1/workspaces").catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.isNetworkError).toBe(true);
    expect(error.code).toBe("network-error");
  });
});

describe("the refresh retry", () => {
  it("exchanges the refresh token once and replays the call", async () => {
    signIn();
    respondWith(
      { status: 401, data: { code: "invalid-token", title: "Unauthorized" } },
      { status: 200, data: tokens("access-2") }, // the refresh exchange
      { status: 200, data: ACCOUNT }, // the replay
    );

    await expect(http.get<Account>("/api/v1/auth/me")).resolves.toEqual(ACCOUNT);

    expect(requests).toHaveLength(3);
    expect(requests[2].headers.get("Authorization")).toBe("Bearer access-2");
    expect(getSession()?.accessToken).toBe("access-2");
  });

  it("signs the browser out when the refresh token is itself rejected", async () => {
    signIn();
    respondWith(
      { status: 401, data: { code: "invalid-token" } },
      { status: 401, data: { code: "invalid-token" } }, // the refresh exchange fails too
    );

    const error = (await http.get("/api/v1/auth/me").catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(401);
    expect(getSession()).toBeNull();
    expect(requests).toHaveLength(2); // no third call: the retry is not attempted
  });

  it("does not retry a 401 that carries no credential to refresh", async () => {
    signIn();
    respondWith({ status: 401, data: { code: "unauthorized" } });

    await expect(http.get("/api/v1/auth/me")).rejects.toBeInstanceOf(ApiError);

    expect(requests).toHaveLength(1);
  });
});

describe("an expired session", () => {
  it("refreshes before the call rather than paying for a 401 first", async () => {
    signInExpired();
    respondWith(
      { status: 200, data: tokens("access-2") }, // the pre-emptive exchange
      { status: 200, data: ACCOUNT },
    );

    await expect(http.get<Account>("/api/v1/auth/me")).resolves.toEqual(ACCOUNT);

    // Two calls, not three: the request never went out with the dead token.
    expect(requests).toHaveLength(2);
    expect(requests[1].headers.get("Authorization")).toBe("Bearer access-2");
  });

  it("signs out without calling the API when the refresh token is spent too", async () => {
    signInDead();
    respondWith({ status: 401, data: { code: "unauthorized" } });

    await expect(http.get("/api/v1/auth/me")).rejects.toBeInstanceOf(ApiError);

    expect(getSession()).toBeNull();
    // The request still went out, unauthenticated; what did not happen is a doomed refresh.
    expect(requests).toHaveLength(1);
    expect(requests[0].headers.has("Authorization")).toBe(false);
  });

  it("records why it ended, so the sign-in page can say so", async () => {
    signInDead();
    respondWith({ status: 401, data: { code: "unauthorized" } });

    await http.get("/api/v1/auth/me").catch(() => undefined);

    expect(consumeSessionEndedReason()).toBe("expired");
    // Consumed once: a second navigation must not re-announce it.
    expect(consumeSessionEndedReason()).toBeNull();
  });

  it("calls a rejected but in-date refresh token revoked, not expired", async () => {
    signIn();
    respondWith(
      { status: 401, data: { code: "invalid-token" } },
      { status: 401, data: { code: "invalid-token" } }, // the exchange is refused
    );

    await http.get("/api/v1/auth/me").catch(() => undefined);

    expect(getSession()).toBeNull();
    expect(consumeSessionEndedReason()).toBe("revoked");
  });
});
