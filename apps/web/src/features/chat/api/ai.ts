"use client";

import { useMutation, useQuery } from "@tanstack/react-query";

import { API_URL, ApiError, apiFetch } from "@/lib/api/client";
import type { AskReply, ChatReply, ProviderInfo } from "@/lib/api/types";

export const aiKeys = {
  providers: ["ai", "providers"] as const,
};

export function useProviders() {
  return useQuery({
    queryKey: aiKeys.providers,
    queryFn: () => apiFetch<ProviderInfo>("/api/ai/providers"),
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useChat() {
  return useMutation({
    mutationFn: (input: { message: string; conversationId: string }) =>
      apiFetch<ChatReply>("/api/ai/chat", { method: "POST", body: input }),
  });
}

export function useAsk() {
  return useMutation({
    mutationFn: (input: { question: string; topK?: number; similarityThreshold?: number }) =>
      apiFetch<AskReply>("/api/ai/ask", { method: "POST", body: input }),
  });
}

export function useClearConversation() {
  return useMutation({
    mutationFn: (conversationId: string) =>
      apiFetch<void>(`/api/ai/chat/${encodeURIComponent(conversationId)}`, {
        method: "DELETE",
      }),
  });
}

/**
 * Consumes the API's `text/event-stream`.
 *
 * EventSource cannot POST, so this reads the body stream directly and parses SSE
 * frames by hand: split on the blank line between events, then read the `event:`
 * and `data:` lines. Multiple `data:` lines in one frame are joined with "\n",
 * per the SSE spec — that is what preserves newlines inside a model's answer.
 */
export async function streamChat(
  input: { message: string; conversationId: string },
  handlers: {
    onToken: (token: string) => void;
    onDone?: () => void;
    signal?: AbortSignal;
  },
): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${API_URL}/api/ai/chat/stream`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
      body: JSON.stringify(input),
      signal: handlers.signal,
    });
  } catch (cause) {
    if ((cause as Error)?.name === "AbortError") return;
    throw new ApiError(0, `Cannot reach the API at ${API_URL}. Is it running?`);
  }

  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, text || `${response.status} ${response.statusText}`);
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
