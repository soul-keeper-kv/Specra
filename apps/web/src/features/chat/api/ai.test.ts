import { afterEach, describe, expect, it, vi } from "vitest";

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
});
