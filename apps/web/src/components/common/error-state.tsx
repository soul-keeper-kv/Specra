"use client";

import { CircleAlert, RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";

/**
 * Renders a failed request.
 *
 * The API already translated `detail`, so it is shown verbatim rather than mapped back through a
 * code — re-translating here would mean maintaining the same sentences twice. The request id is
 * surfaced deliberately: it is the string that finds this exact failure in the server log, and a
 * user can paste it into a bug report.
 */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const t = useTranslations("errors");
  const tActions = useTranslations("actions");

  const apiError = error instanceof ApiError ? error : undefined;
  const message = apiError
    ? apiError.isNetworkError
      ? t("network")
      : apiError.message
    : t("generic");
  const reference = apiError?.traceId ?? apiError?.requestId;

  return (
    <div className="grid gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-6">
      <div className="flex items-start gap-2">
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-destructive" />
        <div className="grid gap-1">
          <p className="text-sm font-medium text-destructive">{t("title")}</p>
          <p className="text-sm text-muted-foreground">{message}</p>
          {reference ? (
            <p className="font-mono text-xs text-muted-foreground">
              {t("reference", { id: reference })}
            </p>
          ) : null}
        </div>
      </div>
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
