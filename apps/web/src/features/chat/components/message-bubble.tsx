"use client";

import { Loader2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { Source } from "@/lib/api/types";
import { cn } from "@/lib/utils";

export type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  /** Present only for RAG answers: the chunks the model was given. */
  sources?: Source[];
  pending?: boolean;
  /** The "answer" is really an error message, so it should not look like model output. */
  failed?: boolean;
};

export function MessageBubble({
  message,
  sourcesLabel,
}: {
  message: ChatMessage;
  sourcesLabel: string;
}) {
  const isUser = message.role === "user";

  return (
    <div className={cn("flex", isUser ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[80%] rounded-lg px-3.5 py-2.5 text-sm whitespace-pre-wrap",
          isUser
            ? "bg-primary text-primary-foreground"
            : message.failed
              ? "bg-destructive/10 text-destructive"
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
            <span className="text-xs font-medium text-muted-foreground">{sourcesLabel}</span>
            {message.sources.map((source, index) => (
              <div key={`${source.sourceId}-${index}`} className="flex gap-2 text-xs">
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
  );
}
