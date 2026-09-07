"use client";

import { RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";

import { ContentSkeleton } from "@/components/common/content-skeleton";
import { Button } from "@/components/ui/button";

/** Hides technical request details behind a neutral placeholder and keeps recovery available. */
export function ErrorState({ onRetry }: { error: unknown; onRetry?: () => void }) {
  const tStates = useTranslations("states");
  const tActions = useTranslations("actions");

  return (
    <div
      className="grid gap-4 rounded-lg border bg-card p-6"
      role="status"
      aria-label={tStates("loading")}
    >
      <ContentSkeleton />
      {onRetry ? (
        <div>
          <Button variant="outline" size="sm" onClick={onRetry}>
            <RotateCcw className="size-4" />
            {tActions("retry")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
