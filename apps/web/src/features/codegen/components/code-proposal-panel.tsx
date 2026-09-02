"use client";

import {
  AlertTriangle,
  Check,
  Eye,
  FileCode2,
  GitCommitHorizontal,
  GitCompare,
  Loader2,
  Pencil,
  RefreshCw,
  ScanSearch,
  Sparkles,
  Undo2,
  X,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorState } from "@/components/common/error-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  useApplyGeneration,
  useCodeGeneration,
  useGenerateCode,
  useRejectGeneration,
} from "@/features/codegen/api/code-generations";
import { FileDiff } from "@/features/codegen/components/file-diff";
import { ApiError } from "@/lib/api/client";
import type { GeneratedFile, Impact, StepChange } from "@/lib/api/types";

/**
 * The review surface: what AI proposes, what it would change, and the two buttons only a person
 * can press.
 *
 * Nothing here can commit by itself and nothing auto-applies — the split between "generate" and
 * "apply" is the product's central rule, so the UI shows both steps rather than hiding one.
 */
export function CodeProposalPanel({
  testCaseId,
  hasModel,
}: {
  testCaseId: string | undefined;
  hasModel: boolean;
}) {
  const t = useTranslations("codegen");
  const proposal = useCodeGeneration(testCaseId);
  const generate = useGenerateCode(testCaseId);
  const apply = useApplyGeneration();
  const reject = useRejectGeneration();
  const [selected, setSelected] = useState<string | null>(null);
  /**
   * Off by default. Committing is local and undoable; pushing publishes to a remote other
   * people pull from, so it is a second decision rather than a consequence of the first.
   */
  const [push, setPush] = useState(false);
  /**
   * Corrected bodies by path, empty until somebody types.
   *
   * Kept here rather than sent back to the proposal: the stored row is the record of what the
   * model produced, and the edit is what the reviewer decided to commit instead. They travel
   * together on apply, which is the only call that writes anything.
   */
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState(false);

  if (!testCaseId) {
    return <Empty title={t("empty.notImported")} hint={t("empty.notImportedHint")} />;
  }
  if (!hasModel) {
    return <Empty title={t("empty.noModel")} hint={t("empty.noModelHint")} />;
  }
  if (proposal.isPending) {
    return <Skeleton className="m-4 h-64" />;
  }
  if (proposal.isError) {
    return <ErrorState error={proposal.error} onRetry={() => void proposal.refetch()} />;
  }

  const current = proposal.data;
  /**
   * The proposal as it would be committed. Overlaying the edits here means the diff, the file
   * list and the apply all read the same thing — the reviewer never sees one version and
   * commits another.
   */
  const files = (current?.files ?? []).map((file) =>
    file.path in edits
      ? { ...file, contents: edits[file.path]!, status: "MODIFIED" as const }
      : file,
  );
  const active = files.find((file) => file.path === selected) ?? files[0];
  const changed = files.filter((file) => file.status !== "UNCHANGED").length;
  const edited = Object.keys(edits).length;

  function runGeneration() {
    generate.mutate(undefined, {
      onSuccess: (created) => {
        setSelected(created.files[0]?.path ?? null);
        // Edits belonged to the proposal that was just superseded. Carrying them over would
        // silently paste old corrections into lines that may no longer exist.
        setEdits({});
        setEditing(false);
        toast.success(t("toast.generated", { count: created.files.length }));
      },
    });
  }

  function applyProposal() {
    if (!current) return;
    apply.mutate(
      {
        id: current.id,
        input: {
          push,
          ...(edited > 0
            ? { edits: Object.entries(edits).map(([path, contents]) => ({ path, contents })) }
            : {}),
        },
      },
      {
        onSuccess: (applied) =>
          toast.success(
            t(push ? "toast.appliedAndPushed" : "toast.applied", {
              sha: applied.commitSha?.slice(0, 7) ?? "",
            }),
          ),
        onError: (error) => toast.error(describe(error, t)),
      },
    );
  }

  function editFile(path: string, contents: string) {
    setEdits((previous) => ({ ...previous, [path]: contents }));
  }

  /** Drops the correction rather than storing the original again, so "edited" stops being true. */
  function revertEdit(path: string) {
    setEdits((previous) => {
      const next = { ...previous };
      delete next[path];
      return next;
    });
  }

  function rejectProposal() {
    if (!current) return;
    reject.mutate(current.id, {
      onSuccess: () => toast.success(t("toast.rejected")),
      onError: (error) => toast.error(describe(error, t)),
    });
  }

  return (
    <div className="grid gap-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" disabled={generate.isPending} onClick={runGeneration}>
          {generate.isPending ? (
            <Loader2 className="size-4 animate-spin" />
          ) : current ? (
            <RefreshCw className="size-4" />
          ) : (
            <Sparkles className="size-4" />
          )}
          {current ? t("regenerate") : t("generate")}
        </Button>
        {current ? (
          <>
            <Button
              size="sm"
              variant="outline"
              disabled={apply.isPending}
              onClick={applyProposal}
            >
              {apply.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Check className="size-4" />
              )}
              {t("apply")}
            </Button>
            <div className="flex items-center gap-2">
              <Switch
                id="push-after-apply"
                checked={push}
                onCheckedChange={setPush}
                disabled={apply.isPending}
              />
              <Label htmlFor="push-after-apply" className="text-sm text-muted-foreground">
                {t("pushAfterApply")}
              </Label>
            </div>
            <Button
              size="sm"
              variant="ghost"
              disabled={reject.isPending}
              onClick={rejectProposal}
            >
              <X className="size-4" />
              {t("reject")}
            </Button>
          </>
        ) : null}
      </div>

      {generate.error ? <GenerationProblem error={generate.error} /> : null}

      {current ? (
        <>
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <Badge variant="secondary">{t("status.proposed")}</Badge>
            <span>{t("summary", { files: files.length, changed })}</span>
            {edited > 0 ? (
              <Badge variant="outline" className="font-normal">
                {t("editedCount", { count: edited })}
              </Badge>
            ) : null}
            <span className="font-mono">
              {t("fromModel", { version: current.modelVersion })}
            </span>
          </div>

          {/* What moved in the test case, before the file diff. A reviewer who knows three
              steps changed reads the diff looking for three things; without it they are
              re-deriving the change from the code. */}
          {current.impact ? <ImpactSummary impact={current.impact} /> : null}

          {current.unresolved.length > 0 ? (
            <Alert>
              <ScanSearch className="size-4" />
              <AlertTitle>{t("unresolved.title")}</AlertTitle>
              <AlertDescription>
                <p>{t("unresolved.hint")}</p>
                <ul className="mt-1.5 grid gap-1">
                  {current.unresolved.map((target) => (
                    <li key={`${target.stepId}-${target.page}`} className="font-mono text-xs">
                      {target.page}
                      {target.element ? `.${target.element}` : ""}
                      <span className="ms-2 text-muted-foreground">({target.stepId})</span>
                    </li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          ) : null}

          <div className="grid gap-3 lg:grid-cols-[15rem_minmax(0,1fr)]">
            <ScrollArea className="h-72 rounded-md border">
              <ul className="grid gap-0.5 p-1.5">
                {files.map((file) => (
                  <li key={file.path}>
                    <button
                      type="button"
                      className={`grid w-full grid-cols-[auto_1fr_auto] items-center gap-2 rounded px-2 py-1.5 text-left text-xs transition-colors ${
                        active?.path === file.path
                          ? "bg-accent text-accent-foreground"
                          : "hover:bg-muted/60"
                      }`}
                      onClick={() => setSelected(file.path)}
                    >
                      <FileCode2 className="size-3.5 shrink-0 text-muted-foreground" />
                      <span className="truncate font-mono">{file.path}</span>
                      <FileStatusMark status={file.status} />
                    </button>
                  </li>
                ))}
              </ul>
            </ScrollArea>

            <div className="min-w-0 overflow-hidden rounded-md border">
              {active ? (
                <>
                  <div className="flex items-center justify-between gap-2 border-b bg-muted/40 px-3 py-2">
                    <span className="truncate font-mono text-xs">{active.path}</span>
                    <div className="flex shrink-0 items-center gap-1.5">
                      {active.path in edits ? (
                        <Badge variant="secondary" className="font-normal">
                          {t("edited")}
                        </Badge>
                      ) : null}
                      <Badge variant="outline" className="font-normal">
                        {t(`fileStatus.${active.status}`)}
                      </Badge>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-7 px-2"
                        onClick={() => setEditing((was) => !was)}
                      >
                        {editing ? (
                          <>
                            <Eye className="size-3.5" />
                            {t("viewDiff")}
                          </>
                        ) : (
                          <>
                            <Pencil className="size-3.5" />
                            {t("edit")}
                          </>
                        )}
                      </Button>
                      {active.path in edits ? (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 px-2"
                          onClick={() => revertEdit(active.path)}
                        >
                          <Undo2 className="size-3.5" />
                          {t("revertEdit")}
                        </Button>
                      ) : null}
                    </div>
                  </div>
                  {editing ? (
                    <>
                      <Label htmlFor="generated-file-editor" className="sr-only">
                        {t("editorLabel")}
                      </Label>
                      <Textarea
                        id="generated-file-editor"
                        // Keyed on the path so switching files replaces the editor rather than
                        // carrying one file's caret and scroll position into another's body.
                        key={active.path}
                        value={active.contents}
                        onChange={(event) => editFile(active.path, event.target.value)}
                        spellCheck={false}
                        className="h-64 resize-none rounded-none border-0 font-mono text-xs focus-visible:ring-0"
                      />
                    </>
                  ) : (
                    <ScrollArea className="h-64">
                      <FileDiff file={active} />
                    </ScrollArea>
                  )}
                </>
              ) : null}
            </div>
          </div>

          <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <GitCommitHorizontal className="size-3.5" />
            {t("applyHint")}
          </p>
        </>
      ) : (
        <Empty title={t("empty.noProposal")} hint={t("empty.noProposalHint")} />
      )}
    </div>
  );
}

/**
 * What changed in the test case since the code in the repository was written.
 *
 * The projection is whole and deterministic — it has to be, or it would not be reproducible — so
 * this is the other half of "regeneration is a diff, not a rewrite": the file diff shows what the
 * code does, and this says what the *test case* did, which is the thing a reviewer actually
 * decided to change.
 */
function ImpactSummary({ impact }: { impact: Impact }) {
  const t = useTranslations("codegen.impact");

  return (
    <div className="grid gap-2 rounded-md border bg-muted/30 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <GitCompare className="size-4 text-muted-foreground" />
        <span className="text-xs font-medium">
          {t("summary", { changed: impact.steps.length, unchanged: impact.unchanged })}
        </span>
        {/* A large diff on a minor change means the projection moved, not the test case — worth
            saying, because it is the reviewer's cue that something else is going on. */}
        {impact.minor ? (
          <Badge variant="secondary" className="font-normal">
            {t("minor")}
          </Badge>
        ) : null}
      </div>

      <ul className="grid gap-1">
        {impact.steps.map((delta) => (
          <li key={delta.stepId} className="flex flex-wrap items-center gap-2 text-xs">
            <span className={`font-mono ${CHANGE_COLOUR[delta.change]}`}>
              {CHANGE_MARK[delta.change]}
            </span>
            <span className="rounded bg-muted px-1.5 py-0.5 font-mono">{delta.stepId}</span>
            <span className="text-muted-foreground">{delta.description}</span>
            {delta.page ? (
              <span className="font-mono text-muted-foreground">{delta.page}</span>
            ) : null}
          </li>
        ))}
      </ul>

      {impact.pages.length > 0 ? (
        <p className="text-xs text-muted-foreground">
          {t("pages", { pages: impact.pages.join(", ") })}
        </p>
      ) : null}
    </div>
  );
}

const CHANGE_MARK: Record<StepChange, string> = {
  ADDED: "+",
  REMOVED: "-",
  MODIFIED: "~",
  UNCHANGED: "=",
};

const CHANGE_COLOUR: Record<StepChange, string> = {
  ADDED: "text-emerald-600 dark:text-emerald-400",
  REMOVED: "text-destructive",
  MODIFIED: "text-amber-600 dark:text-amber-400",
  UNCHANGED: "text-muted-foreground",
};

/** A runner refusal names the thing to fix; anything else falls through to the ordinary state. */
function GenerationProblem({ error }: { error: unknown }) {
  const t = useTranslations("codegen.problems");
  const apiError = error instanceof ApiError ? error : undefined;
  const runnerMessage = apiError?.problem?.runnerMessage;

  if (!runnerMessage) {
    return <ErrorState error={error} />;
  }
  return (
    <Alert variant="destructive">
      <AlertTriangle className="size-4" />
      <AlertTitle>{t("refused")}</AlertTitle>
      <AlertDescription>
        <p className="font-mono text-xs">{runnerMessage}</p>
      </AlertDescription>
    </Alert>
  );
}

function FileStatusMark({ status }: { status: GeneratedFile["status"] }) {
  if (status === "NEW") {
    return <span className="shrink-0 font-mono text-emerald-600 dark:text-emerald-400">+</span>;
  }
  if (status === "MODIFIED") {
    return <span className="shrink-0 font-mono text-amber-600 dark:text-amber-400">~</span>;
  }
  return <span className="shrink-0 font-mono text-muted-foreground">=</span>;
}

function Empty({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="p-4">
      <div className="grid justify-items-center gap-3 rounded-md border border-dashed px-4 py-10 text-center">
        <FileCode2 className="size-6 text-muted-foreground" />
        <div>
          <p className="text-sm font-medium">{title}</p>
          <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
        </div>
      </div>
    </div>
  );
}

function describe(error: unknown, t: (key: string) => string): string {
  return error instanceof ApiError ? error.message : t("problems.generic");
}
