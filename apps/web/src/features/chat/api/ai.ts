"use client";

import { useMutation, useQuery } from "@tanstack/react-query";

import {
  API_URL,
  ApiError,
  ensureFreshSession,
  http,
  renewSession,
  streamHeaders,
} from "@/lib/api/client";
import { getSession } from "@/lib/api/session";
import type { ApiProblem, AskReply, ChatReply, ProviderInfo } from "@/lib/api/types";

export const aiKeys = {
  providers: ["ai", "providers"] as const,
};

export function useProviders() {
  return useQuery({
    queryKey: aiKeys.providers,
    queryFn: ({ signal }) => http.get<ProviderInfo>("/api/ai/providers", { signal }),
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useChat() {
  return useMutation({
    mutationFn: (input: { message: string; conversationId: string }) =>
      http.post<ChatReply>("/api/ai/chat", input),
  });
}

export function useAsk() {
  return useMutation({
    mutationFn: (input: { question: string; topK?: number; similarityThreshold?: number }) =>
      http.post<AskReply>("/api/ai/ask", input),
  });
}

export function useClearConversation() {
  return useMutation({
    mutationFn: (conversationId: string) =>
      http.delete(`/api/ai/chat/${encodeURIComponent(conversationId)}`),
  });
}

/**
 * Consumes the API's `text/event-stream`.
 *
 * EventSource cannot POST, so this reads the body stream directly and parses SSE
 * frames by hand: split on the blank line between events, then read the `event:`
 * and `data:` lines. Multiple `data:` lines in one frame are joined with "\n",
 * per the SSE spec — that is what preserves newlines inside a model's answer.
 *
 * This is the one call that does not go through axios: its browser adapters hand back a body
 * only once it is complete, which is the opposite of what a token stream is for. `streamHeaders()`
 * supplies the credential, language and request id the axios interceptor would have added — and
 * the retry below stands in for its response interceptor, so an access token that died mid-session
 * costs the user a second request rather than an error they have to read.
 */
export async function streamChat(
  input: { message: string; conversationId: string },
  handlers: {
    onToken: (token: string) => void;
    onDone?: () => void;
    signal?: AbortSignal;
  },
): Promise<void> {
  // Up front, the same thing the axios request interceptor does: a token already known to be
  // spent is exchanged rather than sent. A refusal here has already ended the session, so there
  // is nothing to send it with.
  const signedIn = getSession() !== null;
  if (signedIn && !(await ensureFreshSession())) throw expired();

  let response = await send(input, handlers.signal);
  if (response === null) return;

  // A 401 that names the token means the credential was presented and did not verify — the same
  // condition the axios interceptor retries. The exchange is unconditional: the browser believes
  // this token is live, and only the server knows otherwise. One attempt, then the error stands.
  if (
    signedIn &&
    response.status === 401 &&
    (await problemCode(response)) === "invalid-token"
  ) {
    if (!(await renewSession())) throw expired();

    response = await send(input, handlers.signal);
    if (response === null) return;
  }

  if (!response.ok || !response.body) {
    throw await streamError(response);
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += value;

      // Frames are separated by a blank line; \r\n is tolerated for proxies that rewrite it.
      let boundary: number;
      while ((boundary = findFrameEnd(buffer)) !== -1) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary).replace(/^(\r?\n){2}/, "");

        const { event, data } = parseFrame(frame);
        if (event === "done") {
          handlers.onDone?.();
          return;
        }
        if (data) {
          const token = decodeToken(data);
          if (token) handlers.onToken(token);
        }
      }
    }
    handlers.onDone?.();
  } finally {
    reader.releaseLock();
  }
}

/**
 * The error for a session that could not be revived.
 *
 * It carries `invalid-token` so the view branches on the same code the axios path would have
 * given it — by this point `endSession` has already run and the route guard is on its way to the
 * sign-in page, so nothing renders this text.
 */
function expired(): ApiError {
  return new ApiError(401, "invalid-token", {
    type: "https://specra.dev/problems/invalid-token",
    title: "Unauthorized",
    status: 401,
    detail: "invalid-token",
    code: "invalid-token",
    timestamp: new Date().toISOString(),
  });
}

/**
 * One attempt at the stream. `null` means the caller aborted, which is not a failure and must not
 * reach the UI as one.
 */
async function send(
  input: { message: string; conversationId: string },
  signal?: AbortSignal,
): Promise<Response | null> {
  try {
    return await fetch(`${API_URL}/api/ai/chat/stream`, {
      method: "POST",
      headers: await streamHeaders(),
      body: JSON.stringify(input),
      signal,
    });
  } catch (cause) {
    if ((cause as Error)?.name === "AbortError") return null;
    throw new ApiError(0, `Cannot reach the API at ${API_URL}. Is it running?`);
  }
}

/**
 * The problem document's `code`, read from a clone so the body stays available to whoever renders
 * the error afterwards. An error page that is not a problem document simply has no code.
 */
async function problemCode(response: Response): Promise<string | undefined> {
  try {
    const problem = (await response.clone().json()) as ApiProblem;
    return problem?.code;
  } catch {
    return undefined;
  }
}

/**
 * Turns a failed response into the same `ApiError` the axios path would have produced — carrying
 * the problem document, so the UI branches on `code` rather than on translated prose.
 */
async function streamError(response: Response): Promise<ApiError> {
  const text = await response.text().catch(() => "");
  let problem: ApiProblem | undefined;
  try {
    problem = JSON.parse(text) as ApiProblem;
  } catch {
    problem = undefined;
  }

  return new ApiError(
    response.status,
    problem?.detail ?? problem?.title ?? (text || `${response.status} ${response.statusText}`),
    problem,
  );
}

function findFrameEnd(buffer: string): number {
  const lf = buffer.indexOf("\n\n");
  const crlf = buffer.indexOf("\r\n\r\n");
  if (lf === -1) return crlf;
  if (crlf === -1) return lf;
  return Math.min(lf, crlf);
}

/**
 * The API sends each token as a JSON string, because SSE framing would otherwise
 * eat a token's leading space (a receiver must strip one space after `data:`) and
 * split a token containing a newline across frames. Both are common in model output.
 */
function decodeToken(data: string): string {
  try {
    const parsed: unknown = JSON.parse(data);
    return typeof parsed === "string" ? parsed : "";
  } catch {
    // Tolerate a plain-text producer; it just loses the whitespace guarantee.
    return data;
  }
}

function parseFrame(frame: string): { event?: string; data: string } {
  let event: string | undefined;
  const data: string[] = [];

  for (const rawLine of frame.split(/\r?\n/)) {
    if (rawLine.startsWith(":")) continue; // comment / keep-alive
    const colon = rawLine.indexOf(":");
    if (colon === -1) continue;
    const field = rawLine.slice(0, colon);
    // A single leading space after the colon is part of the framing, not the value.
    const value = rawLine.slice(colon + 1).replace(/^ /, "");
    if (field === "event") event = value;
    else if (field === "data") data.push(value);
  }

  return { event, data: data.join("\n") };
}
