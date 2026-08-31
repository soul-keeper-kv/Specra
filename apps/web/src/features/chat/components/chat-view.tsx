"use client";

import { Eraser, Loader2, Send, Square } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { streamChat, useAsk, useChat, useClearConversation } from "@/features/chat/api/ai";
import { MessageBubble, type ChatMessage } from "@/features/chat/components/message-bubble";
import { ApiError } from "@/lib/api/client";
import { useUiStore } from "@/stores/ui-store";

/**
 * Three transports behind one composer: plain call, SSE stream, and RAG.
 *
 * They are mutually exclusive rather than composable — RAG answers arrive whole, because the
 * retrieved sources are only known once the advisor has run, so streaming is disabled while it
 * is on rather than silently ignored.
 */
export function ChatView() {
  const t = useTranslations("chat");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");

  const { conversationId, streaming, ragMode, newConversation, setStreaming, setRagMode } =
    useUiStore();

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  const chat = useChat();
  const ask = useAsk();
  const clearConversation = useClearConversation();

  const append = (message: ChatMessage) => setMessages((prev) => [...prev, message]);
  const patchLast = (patch: Partial<ChatMessage>) =>
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
      const message = error instanceof ApiError ? error.message : tErrors("generic");
      patchLast({ text: message, pending: false, failed: true });
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
      // A new id even if the server never confirmed: the local thread is gone either way.
      onSettled: () => newConversation(),
    });
  }

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("title")}
        description={ragMode ? t("subtitleRag") : t("subtitle")}
        actions={
          <div className="flex flex-wrap items-center gap-5">
            <div className="flex items-center gap-2">
              <Switch id="rag" checked={ragMode} onCheckedChange={setRagMode} />
              <Label htmlFor="rag" className="text-sm">
                {t("rag")}
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
                {t("stream")}
              </Label>
            </div>
            <Button variant="ghost" size="sm" onClick={reset}>
              <Eraser className="size-4" />
              {tActions("reset")}
            </Button>
          </div>
        }
      />

      <Card className="overflow-hidden py-0">
        <CardContent className="p-0">
          <ScrollArea className="h-[55vh]">
            <div className="grid gap-4 p-5">
              {messages.length === 0 ? (
                <p className="py-16 text-center text-sm text-muted-foreground">
                  {ragMode ? t("emptyRag") : t("empty")}
                </p>
              ) : (
                messages.map((message) => (
                  <MessageBubble
                    key={message.id}
                    message={message}
                    sourcesLabel={t("sources")}
                  />
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
            // Enter sends; Shift+Enter is a newline, which is what a chat box is expected to do.
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              void send();
            }
          }}
          rows={2}
          placeholder={ragMode ? t("placeholderRag") : t("placeholder")}
          aria-label={t("title")}
          className="resize-none"
        />
        {busy && streaming && !ragMode ? (
          <Button
            variant="outline"
            size="icon"
            aria-label={tActions("stop")}
            onClick={() => abortRef.current?.abort()}
          >
            <Square className="size-4" />
          </Button>
        ) : (
          <Button
            size="icon"
            aria-label={tActions("send")}
            disabled={busy || !input.trim()}
            onClick={() => void send()}
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
          </Button>
        )}
      </div>
    </div>
  );
}
