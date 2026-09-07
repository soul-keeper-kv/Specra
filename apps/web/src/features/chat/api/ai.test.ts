import axios, { AxiosError, type AxiosAdapter } from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  clearSession,
  consumeSessionEndedReason,
  getSession,
  setSession,
} from "@/lib/api/session";
import type { Account } from "@/lib/api/types";

import { streamChat } from "./ai";

/** Feeds a canned SSE body through the real fetch/ReadableStream path. */
function mockSse(chunks: string[]) {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(stream, { status: 200 })));
}

/** Frames a token exactly the way AiController does: JSON-encoded, one data line. */
const tokenFrame = (token: string) => `event:token\ndata:${JSON.stringify(token)}\n\n`;
const DONE_FRAME = 'event:done\ndata:""\n\n';

async function collect(chunks: string[]) {
  mockSse(chunks);
  const tokens: string[] = [];
  let done = false;
  await streamChat(
    { message: "hi", conversationId: "t" },
    { onToken: (t) => tokens.push(t), onDone: () => (done = true) },
  );
  return { tokens, done };
}

afterEach(() => vi.unstubAllGlobals());

describe("streamChat", () => {
  it("emits one token per data frame", async () => {
    const { tokens, done } = await collect([
      tokenFrame("Hello"),
      tokenFrame(" world"),
      DONE_FRAME,
    ]);

    expect(tokens).toEqual(["Hello", " world"]);
    expect(done).toBe(true);
  });

  it("preserves a token's leading space, which raw SSE framing would strip", async () => {
    const { tokens } = await collect([tokenFrame("Hello"), tokenFrame(" world"), DONE_FRAME]);

    expect(tokens.join("")).toBe("Hello world");
  });

  it("preserves newlines inside a single token", async () => {
    const { tokens } = await collect([tokenFrame("line one\nline two"), DONE_FRAME]);

    expect(tokens).toEqual(["line one\nline two"]);
  });

  it("reassembles frames split across network chunks", async () => {
    const frame = tokenFrame("abcdef");
    const cut = 18;
    const { tokens } = await collect([
      frame.slice(0, cut),
      frame.slice(cut) + tokenFrame("ghi"),
      DONE_FRAME,
    ]);

    expect(tokens).toEqual(["abcdef", "ghi"]);
  });

  it("stops at the done event without emitting anything after it", async () => {
    const { tokens } = await collect([tokenFrame("kept"), DONE_FRAME, tokenFrame("after")]);

    expect(tokens).toEqual(["kept"]);
  });

  it("tolerates CRLF framing and ignores keep-alive comments", async () => {
    const { tokens } = await collect([
      ":keep-alive\r\n\r\n",
      'event:token\r\ndata:"ok"\r\n\r\n',
      DONE_FRAME,
    ]);

    expect(tokens).toEqual(["ok"]);
  });

  it("falls back to raw text if a producer does not JSON-encode", async () => {
    const { tokens } = await collect(["event:token\ndata:plain\n\n", DONE_FRAME]);

    expect(tokens).toEqual(["plain"]);
  });

  it("joins multi-line data per the SSE spec", async () => {
    // Our server never does this, but a spec-compliant producer may.
    const { tokens } = await collect(["event:token\ndata:a\ndata:b\n\n", DONE_FRAME]);

    expect(tokens).toEqual(["a\nb"]);
  });

  it("surfaces a non-2xx response as an ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("boom", { status: 502 })));

    await expect(
      streamChat({ message: "hi", conversationId: "t" }, { onToken: () => {} }),
    ).rejects.toMatchObject({ status: 502 });
  });

  it("carries the problem document's code, not only its prose", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: "rate-limited", detail: "Quá nhiều yêu cầu" }), {
          status: 429,
        }),
      ),
    );

    await expect(
      streamChat({ message: "hi", conversationId: "t" }, { onToken: () => {} }),
    ).rejects.toMatchObject({
      status: 429,
      code: "rate-limited",
      message: "Quá nhiều yêu cầu",
    });
  });
});

/**
 * The stream is the one call with no axios interceptor behind it, so the refresh and the single
 * retry it needs live in `streamChat` itself. These lock that down: a session whose access token
 * expired must cost the user a second request, not an error.
 */
describe("streamChat and an expired access token", () => {
  afterEach(() => {
    clearSession();
    vi.unstubAllGlobals();
  });

  const account: Account = {
    id: "u-1",
    email: "qa@specra.dev",
    displayName: "QA",
    status: "ACTIVE",
    locale: "vi",
    lastLoginAt: null,
    createdAt: "2026-01-01T00:00:00Z",
  };

  /** Signed in, but the access token is spent and the refresh token is not. */
  function signInWithSpentAccessToken() {
    setSession({
      user: account,
      accessToken: "old-access",
      expiresAt: "2000-01-01T00:00:00Z",
      refreshToken: "refresh-1",
      refreshExpiresAt: "2999-01-01T00:00:00Z",
    });
  }

  function sseBody(chunks: string[]) {
    return new ReadableStream<Uint8Array>({
      start(controller) {
        const encoder = new TextEncoder();
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
        controller.close();
      },
    });
  }

  /** The refresh exchange is a bare axios call, so it settles through the adapter, not fetch. */
  function refreshRespondsWith(canned: { status: number; data?: unknown }) {
    const adapter: AxiosAdapter = async (config) => {
      const response = {
        data: canned.data,
        status: canned.status,
        statusText: "",
        headers: {},
        config,
      };
      if (canned.status >= 200 && canned.status < 300) return response;
      throw new AxiosError("failed", String(canned.status), config, {}, response);
    };
    axios.defaults.adapter = adapter;
  }

  it("refreshes before sending, so the stream never carries a spent token", async () => {
    signInWithSpentAccessToken();
    refreshRespondsWith({
      status: 200,
      data: {
        user: account,
        accessToken: "new-access",
        expiresAt: "2999-01-01T00:00:00Z",
        refreshToken: "refresh-2",
        refreshExpiresAt: "2999-01-01T00:00:00Z",
      },
    });

    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        new Response(sseBody([tokenFrame("ok"), DONE_FRAME]), { status: 200 }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const tokens: string[] = [];
    await streamChat(
      { message: "hi", conversationId: "t" },
      { onToken: (t) => tokens.push(t) },
    );

    expect(tokens).toEqual(["ok"]);
    expect(getSession()?.accessToken).toBe("new-access");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/api/ai/chat/stream");
    expect(init.headers).toMatchObject({ Authorization: "Bearer new-access" });
  });

  it("signs the user out rather than looping when the refresh is refused", async () => {
    signInWithSpentAccessToken();
    refreshRespondsWith({ status: 401 });

    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      streamChat({ message: "hi", conversationId: "t" }, { onToken: () => {} }),
    ).rejects.toMatchObject({ status: 401 });

    // The stream is never attempted with a credential that cannot work.
    expect(fetchMock).not.toHaveBeenCalled();
    expect(getSession()).toBeNull();
    expect(consumeSessionEndedReason()).toBe("revoked");
  });

  it("retries once when a live token is refused mid-session", async () => {
    setSession({
      user: account,
      accessToken: "old-access",
      // Not yet due by the clock, so nothing refreshes up front — the server is the one that
      // rejects it, which is the case the retry exists for.
      expiresAt: "2999-01-01T00:00:00Z",
      refreshToken: "refresh-1",
      refreshExpiresAt: "2999-01-01T00:00:00Z",
    });
    refreshRespondsWith({
      status: 200,
      data: {
        user: account,
        accessToken: "new-access",
        expiresAt: "2999-01-01T00:00:00Z",
        refreshToken: "refresh-2",
        refreshExpiresAt: "2999-01-01T00:00:00Z",
      },
    });

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "invalid-token", detail: "Phiên đã hết hạn" }), {
          status: 401,
        }),
      )
      .mockResolvedValueOnce(
        new Response(sseBody([tokenFrame("ok"), DONE_FRAME]), { status: 200 }),
      );
    vi.stubGlobal("fetch", fetchMock);

    const tokens: string[] = [];
    await streamChat(
      { message: "hi", conversationId: "t" },
      { onToken: (t) => tokens.push(t) },
    );

    expect(tokens).toEqual(["ok"]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [, retry] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(retry.headers).toMatchObject({ Authorization: "Bearer new-access" });
  });
});
