"use client";

import { AlertOctagon, Loader2, RefreshCw, Sparkles } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAnalyseFailure, useFailureAnalysis } from "@/features/analysis/api/analysis";
import { ApiError } from "@/lib/api/client";
import type { RootCause } from "@/lib/api/types";

/**
 * What the evidence says about one failed cell.
 *
 * Only offered for FAILED, never for ERROR: an error means the run could not complete, which is
 * ours to fix and says nothing about the application — the API refuses it, and a button that
 * exists only to be refused is worse than no button.
 */
export function FailureAnalysisPanel({
  itemId,
  analysable,
}: {
  itemId: string;
  analysable: boolean;
}) {
  const t = useTranslations("analysis");
  const stored = useFailureAnalysis(itemId, analysable);
  const analyse = useAnalyseFailure();

  if (!analysable) return null;

  function run(reanalyse: boolean) {
    analyse.mutate(
      { itemId, reanalyse },
      { onError: (error) => toast.error(describe(error, t)) },
    );
  }

  const analysis = stored.data;

  if (!analysis) {
    return (
      <Button
        size="sm"
        variant="outline"
        className="justify-self-start"
        disabled={analyse.isPending || stored.isPending}
        onClick={() => run(false)}
      >
        {analyse.isPending ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <Sparkles className="size-4" />
        )}
        {t("analyse")}
      </Button>
    );
  }

  return (
    <div className="grid gap-2 rounded-lg border bg-muted/30 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <RootCauseBadge cause={analysis.rootCause} />
        <span className="text-xs text-muted-foreground">
          {t("confidence", { value: analysis.confidence })}
        </span>
        <Button
          size="sm"
          variant="ghost"
          className="ms-auto h-7 px-2"
          disabled={analyse.isPending}
          onClick={() => run(true)}
        >
          {analyse.isPending ? (
            <Loader2 className="size-3.5 animate-spin" />
          ) : (
            <RefreshCw className="size-3.5" />
          )}
          {t("reanalyse")}
        </Button>
      </div>

      <p className="text-sm font-medium">{analysis.summary}</p>
      <p className="text-xs text-muted-foreground">{analysis.rationale}</p>

      {/* The whole point of the feature, so it is stated as loudly as a finding rather than
          tucked into the rationale: when the application regressed, there is nothing to fix
          in the test, and offering a diff here would be the tool at its most harmful. */}
      {analysis.rootCause === "PRODUCT_BUG" ? (
        <Alert variant="destructive">
          <AlertOctagon className="size-4" />
          <AlertTitle>{t("productBug.title")}</AlertTitle>
          <AlertDescription>{t("productBug.description")}</AlertDescription>
        </Alert>
      ) : analysis.suggestion ? (
        <div className="grid gap-1 rounded-md border bg-background p-2">
          <p className="text-xs font-medium">{t("suggestion")}</p>
          <p className="text-xs text-muted-foreground">{analysis.suggestion}</p>
        </div>
      ) : null}

      {analysis.confidence < 50 ? (
        <p className="text-xs text-muted-foreground">{t("lowConfidence")}</p>
      ) : null}
    </div>
  );
}

/** Destructive only for a product bug: the others describe a test to mend, not an outage. */
function RootCauseBadge({ cause }: { cause: RootCause }) {
  const t = useTranslations("analysis.rootCause");
  return (
    <Badge variant={cause === "PRODUCT_BUG" ? "destructive" : "secondary"}>{t(cause)}</Badge>
  );
}

/** Branches on `code`, never on the message — the message is translated per request. */
function describe(error: unknown, t: ReturnType<typeof useTranslations<"analysis">>): string {
  if (error instanceof ApiError) {
    if (error.code === "conflict") {
      return error.problem?.detail ?? t("problems.conflict");
    }
    if (error.code === "ai-provider-unavailable" || error.code === "ai-provider-error") {
      return t("problems.provider");
    }
  }
  return t("problems.generic");
}
