"use client";

import { Loader2, Play, PlayCircle, Server } from "lucide-react";
import { useFormatter, useNow, useTranslations } from "next-intl";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useEnvironments } from "@/features/environments/api/environments";
import { useRequestRun, useRuns } from "@/features/runs/api/runs";
import { RunStatusBadge } from "@/features/runs/components/run-badges";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { Run } from "@/lib/api/types";

/**
 * A project's runs, newest first, with the button that starts another.
 *
 * The list re-fetches itself while anything on it is still going, so a queued run becomes a
 * running one and then a result without anybody pressing reload.
 */
export function RunsView({ projectId }: { projectId: string }) {
  const t = useTranslations("runs");
  const runs = useRuns(projectId);
  const request = useRequestRun(projectId);

  // A run with nowhere to point is refused by the API, which is correct but arrives as a toast
  // the user cannot act on. Knowing the list is empty turns that into a link to the screen that
  // fixes it.
  const environments = useEnvironments(projectId);
  const needsEnvironment = environments.data?.length === 0;

  function startRun() {
    request.mutate(
      {},
      {
        onSuccess: (run) => toast.success(t("toast.queued", { reference: run.reference })),
        onError: (error) => toast.error(describe(error, t)),
      },
    );
  }

  if (runs.isPending) {
    return <Skeleton className="m-4 h-64" />;
  }
  if (runs.isError) {
    return <ErrorState error={runs.error} onRetry={() => void runs.refetch()} />;
  }

  const rows = runs.data?.content ?? [];

  return (
    <div className="grid gap-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        <Button size="sm" disabled={request.isPending || needsEnvironment} onClick={startRun}>
          {request.isPending ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Play className="size-4" />
          )}
          {t("run")}
        </Button>
      </div>

      {needsEnvironment ? (
        <EmptyState
          icon={Server}
          title={t("noEnvironment.title")}
          description={t("noEnvironment.description")}
          action={
            <Button asChild size="sm" variant="outline">
              <Link href={`/projects/${projectId}/environments`}>
                {t("noEnvironment.action")}
              </Link>
            </Button>
          }
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={PlayCircle}
          title={t("empty.title")}
          description={t("empty.description")}
        />
      ) : (
        <ul className="grid gap-2">
          {rows.map((run) => (
            <RunRow key={run.id} run={run} />
          ))}
        </ul>
      )}
    </div>
  );
}

function RunRow({ run }: { run: Run }) {
  const t = useTranslations("runs");
  const format = useFormatter();
  // A shared reference instant, ticking on the client. Without it every relativeTime call
  // reads its own clock, which differs between the server render and the browser.
  const now = useNow({ updateInterval: 60_000 });

  return (
    <li>
      <Link
        href={`/runs/${run.id}`}
        className="grid gap-2 rounded-lg border p-3 transition-colors hover:bg-muted/50 sm:grid-cols-[8rem_1fr_auto] sm:items-center"
      >
        <span className="font-mono text-sm font-medium">{run.reference}</span>

        <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <RunStatusBadge status={run.status} />
          <span>
            {t("totals", {
              passed: run.totals.passed,
              total: run.totals.total,
            })}
          </span>
          {/* Seven characters is what a person reads a sha as; the rest is noise here. */}
          <span className="font-mono">{run.commitSha.slice(0, 7)}</span>
          {run.dirty ? <span className="text-destructive">{t("dirty")}</span> : null}
        </span>

        <span className="text-xs text-muted-foreground">
          {format.relativeTime(new Date(run.queuedAt), now)}
        </span>
      </Link>
    </li>
  );
}

/** Branches on `code`, never on the message — the message is translated per request. */
function describe(error: unknown, t: ReturnType<typeof useTranslations<"runs">>): string {
  if (error instanceof ApiError) {
    if (error.code === "conflict") {
      return error.problem?.detail ?? t("problems.conflict");
    }
    if (error.code === "resource-not-found") {
      return t("problems.noEnvironment");
    }
  }
  return t("problems.generic");
}
