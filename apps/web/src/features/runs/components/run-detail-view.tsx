"use client";

import {
  Ban,
  Download,
  FileVideo,
  Image as ImageIcon,
  Loader2,
  ScrollText,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorState } from "@/components/common/error-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FailureAnalysisPanel } from "@/features/analysis/components/failure-analysis-panel";
import { useArtifactLinks, useCancelRun, useRun } from "@/features/runs/api/runs";
import { RunItemStatusBadge, RunStatusBadge } from "@/features/runs/components/run-badges";
import type { ArtifactKind, RunItem } from "@/lib/api/types";

/**
 * One run: every matrix cell, why each failed, and the evidence it left.
 *
 * The failing step is shown as its IR step id rather than a line number, which is what lets a
 * reader match the failure to the manual step they wrote rather than to a file they did not.
 */
export function RunDetailView({ runId }: { runId: string }) {
  const t = useTranslations("runs");
  const format = useFormatter();
  const run = useRun(runId);
  const cancel = useCancelRun();

  if (run.isPending) {
    return <Skeleton className="m-4 h-64" />;
  }
  if (run.isError) {
    return <ErrorState error={run.error} onRetry={() => void run.refetch()} />;
  }
  if (!run.data) {
    return null;
  }

  const current = run.data;
  const inFlight = current.status === "QUEUED" || current.status === "RUNNING";

  return (
    <div className="grid gap-4 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="font-mono text-lg font-semibold">{current.reference}</h1>
        <RunStatusBadge status={current.status} />
        <span className="text-sm text-muted-foreground">
          {t("totals", { passed: current.totals.passed, total: current.totals.total })}
        </span>
        {inFlight ? (
          <Button
            size="sm"
            variant="ghost"
            disabled={cancel.isPending}
            onClick={() =>
              cancel.mutate(current.id, {
                onSuccess: () => toast.success(t("toast.cancelled")),
              })
            }
          >
            <Ban className="size-4" />
            {t("cancel")}
          </Button>
        ) : null}
      </div>

      <dl className="grid gap-x-6 gap-y-1 text-xs text-muted-foreground sm:grid-cols-[auto_1fr]">
        <dt>{t("commit")}</dt>
        <dd className="font-mono">
          {current.commitSha.slice(0, 12)}
          {current.dirty ? <span className="ms-2 text-destructive">{t("dirty")}</span> : null}
        </dd>
        <dt>{t("queued")}</dt>
        <dd>{format.dateTime(new Date(current.queuedAt), "medium")}</dd>
      </dl>

      {/* An ERROR is about us, not about the tests, so it is stated rather than left to be
          inferred from a wall of red cells. */}
      {current.errorMessage ? (
        <Alert>
          <AlertTitle>{t("errored")}</AlertTitle>
          <AlertDescription className="font-mono text-xs">
            {current.errorMessage}
          </AlertDescription>
        </Alert>
      ) : null}

      <ul className="grid gap-2">
        {current.items.map((item) => (
          <RunItemRow key={item.id} item={item} />
        ))}
      </ul>
    </div>
  );
}

function RunItemRow({ item }: { item: RunItem }) {
  const t = useTranslations("runs");
  const [showEvidence, setShowEvidence] = useState(false);
  const failed = item.status === "FAILED" || item.status === "ERROR";

  return (
    <li className="grid gap-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <RunItemStatusBadge status={item.status} />
        <span className="text-sm font-medium">{item.testCaseReference ?? item.title}</span>
        <span className="font-mono text-xs text-muted-foreground">{item.browser}</span>
        {item.durationMs != null ? (
          <span className="text-xs text-muted-foreground">
            {(item.durationMs / 1000).toFixed(1)}s
          </span>
        ) : null}
      </div>

      <p className="font-mono text-xs text-muted-foreground">{item.specPath}</p>

      {failed && item.errorMessage ? (
        <div className="grid gap-1">
          {item.failedStepId ? (
            <p className="text-xs">
              {t("failedAt")}{" "}
              <span className="rounded bg-muted px-1.5 py-0.5 font-mono">
                {item.failedStepId}
              </span>
              {item.errorType ? (
                <span className="ms-2 text-muted-foreground">{item.errorType}</span>
              ) : null}
            </p>
          ) : null}
          <pre className="overflow-x-auto rounded bg-muted/60 p-2 text-xs whitespace-pre-wrap">
            {item.errorMessage}
          </pre>
        </div>
      ) : null}

      {/* Only a FAILED cell: an ERROR never reached the application, so there is nothing about
          it a model could read. */}
      <FailureAnalysisPanel itemId={item.id} analysable={item.status === "FAILED"} />

      {item.artifacts.length > 0 ? (
        showEvidence ? (
          <Evidence itemId={item.id} />
        ) : (
          <Button
            size="sm"
            variant="outline"
            className="justify-self-start"
            onClick={() => setShowEvidence(true)}
          >
            {t("evidence.show", { count: item.artifacts.length })}
          </Button>
        )
      ) : null}
    </li>
  );
}

const ARTIFACT_ICON: Record<ArtifactKind, typeof Download> = {
  SCREENSHOT: ImageIcon,
  VIDEO: FileVideo,
  TRACE: Download,
  LOG: ScrollText,
  DOM: ScrollText,
};

/**
 * Links are fetched when the user asks for them, not with the run.
 *
 * Every link expires, so one minted with the page and read twenty minutes later would be a
 * broken download. Asking at the moment of use is what keeps them valid.
 */
function Evidence({ itemId }: { itemId: string }) {
  const t = useTranslations("runs");
  const links = useArtifactLinks(itemId);

  if (links.isPending) {
    return <Loader2 className="size-4 animate-spin text-muted-foreground" />;
  }
  if (links.isError) {
    return <ErrorState error={links.error} onRetry={() => void links.refetch()} />;
  }

  return (
    <ul className="flex flex-wrap gap-2">
      {(links.data ?? []).map((link) => {
        const Icon = ARTIFACT_ICON[link.kind];
        return (
          <li key={link.id}>
            {/* A plain anchor: the bytes come from the store, never through this app. */}
            <a
              href={link.url}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs transition-colors hover:bg-muted"
            >
              <Icon className="size-3.5" />
              {t(`evidence.kind.${link.kind}`)}
            </a>
          </li>
        );
      })}
    </ul>
  );
}
