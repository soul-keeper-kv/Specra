"use client";

import { Eraser, Loader2, Send, Square } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { streamChat, useAsk, useChat, useClearConversation } from "@/lib/api/ai";
import { ApiError } from "@/lib/api/client";
import type { Source } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/stores/ui-store";

type Message = {
  id: string;
  role: "user" | "assistant";
  text: string;
  sources?: Source[];
  pending?: boolean;
};

export default function ChatPage() {
  const { conversationId, streaming, ragMode, newConversation, setStreaming, setRagMode } =
    useUiStore();

  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const chat = useChat();
  const ask = useAsk();
  const clearConversation = useClearConversation();

  const append = (message: Message) => setMessages((prev) => [...prev, message]);
  const patchLast = (patch: Partial<Message>) =>
    setMessages((prev) => prev.map((m, i) => (i === prev.length - 1 ? { ...m, ...patch } : m)));

  async function send() {
    const text = input.trim();
    if (!text || busy) return;

    setInput("");
    setBusy(true);
    append({ id: crypto.randomUUID(), role: "user", text });
    append({ id: crypto.randomUUID(), role: "assistant", text: "", pending: true });

    try {
      if (ragMode) {
        const reply = await ask.mutateAsync({ question: text });
        patchLast({ text: reply.answer, sources: reply.sources, pending: false });
      } else if (streaming) {
        const controller = new AbortController();
        abortRef.current = controller;
        await streamChat(
          { message: text, conversationId },
          {
            signal: controller.signal,
            onToken: (token) =>
              setMessages((prev) =>
                prev.map((m, i) =>
                  i === prev.length - 1 ? { ...m, text: m.text + token, pending: false } : m,
                ),
              ),
            onDone: () => patchLast({ pending: false }),
          },
        );
      } else {
        const reply = await chat.mutateAsync({ message: text, conversationId });
        patchLast({ text: reply.content, pending: false });
      }
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Request failed";
      patchLast({ text: message, pending: false });
      toast.error(message);
    } finally {
      abortRef.current = null;
      setBusy(false);
    }
  }

  function reset() {
    abortRef.current?.abort();
    setMessages([]);
    clearConversation.mutate(conversationId, {
      onSettled: () => newConversation(),
    });
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-semibold tracking-tight">Chat</h1>
          <p className="text-sm text-muted-foreground">
            {ragMode
              ? "Answers are grounded in the notes you indexed into pgvector."
              : "Plain conversation. History is kept in PostgreSQL."}
          </p>
        </div>

        <div className="flex items-center gap-5">
          <div className="flex items-center gap-2">
            <Switch id="rag" checked={ragMode} onCheckedChange={setRagMode} />
            <Label htmlFor="rag" className="text-sm">
              RAG
            </Label>
          </div>
          <div className="flex items-center gap-2">
            <Switch
              id="stream"
              checked={streaming}
              disabled={ragMode}
              onCheckedChange={setStreaming}
            />
            <Label htmlFor="stream" className="text-sm">
              Stream
            </Label>
          </div>
          <Button variant="ghost" size="sm" onClick={reset}>
            <Eraser className="size-4" />
            Reset
          </Button>
        </div>
      </div>

      <Card className="overflow-hidden py-0">
        <CardContent className="p-0">
          <ScrollArea className="h-[55vh]">
            <div className="grid gap-4 p-5">
              {messages.length === 0 ? (
                <p className="py-16 text-center text-sm text-muted-foreground">
                  {ragMode
                    ? "Ask about something you have indexed."
                    : "Say something to get started."}
                </p>
              ) : (
                messages.map((message) => (
                  <div
                    key={message.id}
                    className={cn(
                      "flex",
                      message.role === "user" ? "justify-end" : "justify-start",
                    )}
                  >
                    <div
                      className={cn(
                        "max-w-[80%] rounded-lg px-3.5 py-2.5 text-sm whitespace-pre-wrap",
                        message.role === "user"
                          ? "bg-primary text-primary-foreground"
                          : "bg-muted",
                      )}
                    >
                      {message.pending && !message.text ? (
                        <Loader2 className="size-4 animate-spin" />
                      ) : (
                        message.text
                      )}

                      {message.sources && message.sources.length > 0 ? (
                        <div className="mt-3 grid gap-1.5 border-t pt-2.5">
                          <span className="text-xs font-medium text-muted-foreground">
                            Retrieved from
                          </span>
                          {message.sources.map((source, i) => (
                            <div key={`${source.noteId}-${i}`} className="flex gap-2 text-xs">
                              <Badge variant="outline" className="shrink-0 font-normal">
                                {source.title}
                              </Badge>
                              {source.score != null ? (
                                <span className="text-muted-foreground tabular-nums">
                                  {source.score.toFixed(2)}
                                </span>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </CardContent>
      </Card>

      <div className="flex items-end gap-2">
        <Textarea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              void send();
            }
          }}
          rows={2}
          placeholder={ragMode ? "Ask about your notes…" : "Message… (Enter to send)"}
          className="resize-none"
        />
        {busy && streaming && !ragMode ? (
          <Button variant="outline" size="icon" onClick={() => abortRef.current?.abort()}>
            <Square className="size-4" />
          </Button>
        ) : (
          <Button size="icon" disabled={busy || !input.trim()} onClick={() => void send()}>
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
          </Button>
        )}
      </div>
    </div>
  );
}
