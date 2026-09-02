"use client";

import {
  ArrowLeft,
  Bot,
  CheckCircle2,
  CircleDot,
  Code2,
  ExternalLink,
  FileInput,
  ListChecks,
  Loader2,
  Lock,
  PanelRight,
  RefreshCw,
  ScanSearch,
  Sparkles,
  Workflow,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorState } from "@/components/common/error-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { StatusBadge } from "@/features/testcases/components/testcase-badges";
import {
  useExternalTest,
  useImportExternalTest,
  useImportedTestCase,
} from "@/features/testmanagement/api/test-management";
import { CodeProposalPanel } from "@/features/codegen/components/code-proposal-panel";
import { useModelTestCase, useTestModel } from "@/features/testmodel/api/test-models";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type {
  ExternalTestDetail,
  TestCase,
  TestModel,
  TestModelStep,
  TestModelTarget,
  TestModelValue,
} from "@/lib/api/types";

/** One manual step, whichever side it was read from. */
type ManualStep = {
  position: number;
  action: string;
  data: string | null;
  expected: string | null;
};

/** What the centre stage is showing: a manual step or one step of the model. */
type Selection = { kind: "manual"; position: number } | { kind: "model"; id: string };

type Rail = "case" | "model" | "script";

type Section = "setup" | "steps" | "teardown";

const SOURCE_PREFIX = "ts-";

/**
 * The automation workspace for one external test: the manual steps on the left, the step under
 * review in the middle, the pipeline and what the model needs on the right. Every state here is
 * real — imported or not, modelled or not, refused with questions — and nothing pretends to be an
 * execution.
 */
export function ExternalTestWorkspace({
  projectId,
  externalId,
}: {
  projectId: string;
  externalId: string;
}) {
  const t = useTranslations("testManagement.workspace");
  const test = useExternalTest(projectId, externalId);
  const linked = useImportedTestCase(projectId, externalId);
  const testCaseId = linked.data?.id;
  const model = useTestModel(testCaseId);
  const importTest = useImportExternalTest(projectId, externalId);
  const modelTest = useModelTestCase(testCaseId);

  const [selection, setSelection] = useState<Selection>({ kind: "manual", position: 1 });
  const [rail, setRail] = useState<Rail>("case");

  if (test.isPending || linked.isPending) {
    return <Skeleton className="h-[42rem] w-full" />;
  }
  if (test.isError) {
    return <ErrorState error={test.error} onRetry={() => void test.refetch()} />;
  }
  if (linked.isError) {
    return <ErrorState error={linked.error} onRetry={() => void linked.refetch()} />;
  }

  const external = test.data;
  const testCase = linked.data;
  const current = model.data ?? null;
  const manualSteps = manualStepsOf(external, testCase);
  const modelSteps = current ? modelStepsOf(current) : [];
  const coverage = new Map(
    current?.coverage.map((c) => [c.sourceStepId, c.modelStepIds]) ?? [],
  );

  function prepareAutomation() {
    importTest.mutate(undefined, {
      onSuccess: (created) =>
        toast.success(t("toast.imported", { reference: created.reference })),
    });
  }

  function runModelling() {
    modelTest.mutate(undefined, {
      onSuccess: (created) => {
        setRail("model");
        setSelection(
          created.document.steps[0]
            ? { kind: "model", id: created.document.steps[0].id }
            : { kind: "manual", position: 1 },
        );
        toast.success(t("toast.modelled", { version: created.version }));
      },
    });
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button asChild variant="ghost" size="sm" className="-ml-2">
          <Link href={`/projects/${projectId}/integrations`}>
            <ArrowLeft className="size-4" />
            {t("back")}
          </Link>
        </Button>
        <Badge variant="outline">Xray</Badge>
        <span className="font-mono text-sm font-semibold">{external.externalId}</span>
        {testCase ? (
          <>
            <Button asChild variant="link" size="sm" className="h-auto p-0 font-mono">
              <Link href={`/projects/${projectId}/test-cases/${testCase.id}`}>
                {testCase.reference}
              </Link>
            </Button>
            <StatusBadge status={testCase.automationStatus} />
          </>
        ) : null}
        <div className="ms-auto flex gap-2">
          <Button asChild variant="outline" size="sm">
            <a href={external.url} target="_blank" rel="noreferrer">
              <ExternalLink className="size-4" />
              {t("openSource")}
            </a>
          </Button>
          {testCase ? (
            <Button size="sm" disabled={modelTest.isPending} onClick={runModelling}>
              {modelTest.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : current ? (
                <RefreshCw className="size-4" />
              ) : (
                <Sparkles className="size-4" />
              )}
              {modelTest.isPending ? t("modelling") : current ? t("remodel") : t("model")}
            </Button>
          ) : (
            <Button size="sm" disabled={importTest.isPending} onClick={prepareAutomation}>
              {importTest.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Bot className="size-4" />
              )}
              {t("prepare")}
            </Button>
          )}
        </div>
      </div>

      {importTest.error ? <ErrorState error={importTest.error} /> : null}
      {modelTest.error ? (
        <ModellingProblem
          error={modelTest.error}
          editHref={testCase ? `/projects/${projectId}/test-cases/${testCase.id}` : undefined}
        />
      ) : null}

      <div className="grid min-h-[42rem] overflow-hidden rounded-lg border bg-card lg:grid-cols-[19rem_minmax(0,1fr)_18rem]">
        <aside className="min-w-0 border-b lg:border-r lg:border-b-0">
          <Tabs
            value={rail}
            onValueChange={(value) => setRail(value as Rail)}
            className="gap-0"
          >
            <TabsList className="m-3 grid w-[calc(100%-1.5rem)] grid-cols-3">
              <TabsTrigger value="case">
                <ListChecks className="size-4" />
                {t("tabs.case")}
              </TabsTrigger>
              <TabsTrigger value="model">
                <Workflow className="size-4" />
                {t("tabs.model")}
              </TabsTrigger>
              <TabsTrigger value="script">
                <Code2 className="size-4" />
                {t("tabs.script")}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="case" className="mt-0">
              <div className="border-y px-4 py-3">
                <p className="line-clamp-2 text-sm font-semibold">
                  {testCase?.title ?? external.title}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {t("stepCount", { count: manualSteps.length })}
                  {" · "}
                  {testCase
                    ? t("source.specra", { reference: testCase.reference })
                    : t("source.external")}
                </p>
              </div>
              <ScrollArea className="h-[32rem]">
                <ol className="grid gap-1 p-2">
                  {manualSteps.map((step) => {
                    const active =
                      selection.kind === "manual" && selection.position === step.position;
                    const covered = coverage.get(sourceId(step.position));
                    return (
                      <li key={step.position}>
                        <button
                          type="button"
                          className={`grid w-full grid-cols-[1.75rem_1fr_auto] gap-2 rounded-md px-2 py-2.5 text-left text-sm transition-colors ${
                            active ? "bg-accent text-accent-foreground" : "hover:bg-muted/60"
                          }`}
                          aria-current={active ? "step" : undefined}
                          onClick={() =>
                            setSelection({ kind: "manual", position: step.position })
                          }
                        >
                          {active ? (
                            <CircleDot className="mt-0.5 size-4 text-primary" />
                          ) : (
                            <span className="font-mono text-xs text-muted-foreground">
                              {step.position}
                            </span>
                          )}
                          <span className="line-clamp-3">{step.action}</span>
                          {current ? (
                            <span
                              className={`mt-1.5 size-2 rounded-full ${
                                covered?.length ? "bg-primary" : "border border-destructive"
                              }`}
                              aria-label={
                                covered?.length ? t("steps.covered") : t("steps.uncovered")
                              }
                            />
                          ) : null}
                        </button>
                      </li>
                    );
                  })}
                </ol>
              </ScrollArea>
            </TabsContent>

            <TabsContent value="model" className="mt-0">
              {current ? (
                <>
                  <div className="border-y px-4 py-3">
                    <p className="line-clamp-2 text-sm font-semibold">
                      {current.document.name}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {t("modelTab.version", {
                        version: current.version,
                        count: modelSteps.length,
                      })}
                    </p>
                  </div>
                  <ScrollArea className="h-[32rem]">
                    <ol className="grid gap-1 p-2">
                      {modelSteps.map(({ step, section }) => {
                        const active = selection.kind === "model" && selection.id === step.id;
                        return (
                          <li key={step.id}>
                            <button
                              type="button"
                              className={`grid w-full gap-1 rounded-md px-2 py-2 text-left text-sm transition-colors ${
                                active
                                  ? "bg-accent text-accent-foreground"
                                  : "hover:bg-muted/60"
                              }`}
                              aria-current={active ? "step" : undefined}
                              onClick={() => setSelection({ kind: "model", id: step.id })}
                            >
                              <span className="flex items-center gap-2">
                                <span className="font-mono text-xs text-muted-foreground">
                                  {step.id}
                                </span>
                                <ActionBadge action={step.action} />
                                {section !== "steps" ? (
                                  <Badge variant="outline" className="font-normal">
                                    {t(`modelTab.section.${section}`)}
                                  </Badge>
                                ) : null}
                                {step.derived ? (
                                  <Badge variant="outline" className="font-normal">
                                    {t("steps.derived")}
                                  </Badge>
                                ) : null}
                              </span>
                              <span className="line-clamp-2 text-xs">
                                {step.description ?? describeTarget(step.target)}
                              </span>
                            </button>
                          </li>
                        );
                      })}
                    </ol>
                  </ScrollArea>
                </>
              ) : (
                <EmptyRail
                  icon={Workflow}
                  title={t("modelTab.empty")}
                  hint={t("modelTab.hint")}
                />
              )}
            </TabsContent>

            <TabsContent value="script" className="mt-0">
              <EmptyRail icon={Code2} title={t("script.rail")} hint={t("script.railHint")} />
            </TabsContent>
          </Tabs>
        </aside>

        <main className="grid min-w-0 grid-rows-[auto_1fr] bg-muted/20">
          <div className="flex items-center justify-between border-b bg-card px-4 py-3">
            <div>
              <p className="text-sm font-semibold">
                {rail === "script" ? t("canvas.scriptTitle") : t("canvas.title")}
              </p>
              <p className="text-xs text-muted-foreground">
                {rail === "script" ? t("canvas.scriptMode") : t("canvas.reviewMode")}
              </p>
            </div>
            {rail === "script" ? null : selection.kind === "manual" ? (
              <Badge variant="secondary">
                {t("canvas.step", { current: selection.position, total: manualSteps.length })}
              </Badge>
            ) : (
              <Badge variant="secondary">{t("canvas.modelStep", { id: selection.id })}</Badge>
            )}
          </div>
          <div className="grid place-items-start p-4 sm:p-8">
            <div className="w-full max-w-3xl overflow-hidden rounded-lg border bg-background shadow-sm">
              <div className="flex items-center gap-2 border-b bg-muted/50 px-3 py-2">
                <span className="size-2.5 rounded-full bg-border" />
                <span className="size-2.5 rounded-full bg-border" />
                <span className="size-2.5 rounded-full bg-border" />
                <div className="mx-auto flex h-6 w-1/2 items-center justify-center rounded bg-background text-[10px] text-muted-foreground">
                  {t("canvas.application")}
                </div>
              </div>
              <div className="grid min-h-80 p-6 sm:p-8">
                {rail === "script" ? (
                  <CodeProposalPanel testCaseId={testCaseId} hasModel={Boolean(current)} />
                ) : selection.kind === "manual" ? (
                  <ManualStage
                    step={manualSteps.find((s) => s.position === selection.position)}
                    model={current}
                    derived={modelSteps
                      .map((m) => m.step)
                      .filter((m) => m.sourceStepIds.includes(sourceId(selection.position)))}
                    onSelectModelStep={(id) => {
                      setRail("model");
                      setSelection({ kind: "model", id });
                    }}
                  />
                ) : (
                  <ModelStage
                    step={modelSteps.find((m) => m.step.id === selection.id)?.step}
                    manualSteps={manualSteps}
                    onSelectManualStep={(position) => {
                      setRail("case");
                      setSelection({ kind: "manual", position });
                    }}
                  />
                )}
              </div>
              <div className="border-t bg-muted/30 px-4 py-3 text-center text-xs text-muted-foreground">
                {t("canvas.runnerHint")}
              </div>
            </div>
          </div>
        </main>

        <aside className="min-w-0 border-t lg:border-t-0 lg:border-l">
          <div className="flex items-center gap-2 border-b px-4 py-3">
            <PanelRight className="size-4" />
            <p className="text-sm font-semibold">{t("details.title")}</p>
          </div>
          <ScrollArea className="h-[38rem]">
            <div className="grid gap-5 p-4">
              <section className="grid gap-3 rounded-md border bg-muted/30 p-3">
                <div className="flex items-center gap-2">
                  <FileInput className="size-4" />
                  <p className="text-sm font-medium">{t("pipeline.title")}</p>
                </div>
                <PipelineItem done label={t("pipeline.source")} />
                <PipelineItem
                  done={Boolean(testCase)}
                  label={
                    testCase
                      ? t("pipeline.imported", { reference: testCase.reference })
                      : t("pipeline.notImported")
                  }
                />
                <PipelineItem
                  done={Boolean(current)}
                  label={
                    current
                      ? t("pipeline.model", { version: current.version })
                      : t("pipeline.noModel")
                  }
                />
                <PipelineItem label={t("pipeline.script")} />
                <PipelineItem label={t("pipeline.run")} />
              </section>

              {current ? (
                <>
                  <section className="grid gap-2">
                    <div className="flex items-center gap-2">
                      <ScanSearch className="size-4" />
                      <p className="text-sm font-medium">{t("pages.title")}</p>
                    </div>
                    {current.pages.length ? (
                      <ul className="grid gap-2">
                        {current.pages.map((page) => (
                          <li key={page.name} className="rounded-md border p-2.5">
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-mono text-xs font-semibold">
                                {page.name}
                              </span>
                              <Badge
                                variant="outline"
                                className="border-amber-500/50 font-normal text-amber-700 dark:text-amber-400"
                              >
                                {t("pages.needsInspection")}
                              </Badge>
                            </div>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {t("pages.elements", { count: page.elements.length })}
                            </p>
                            {page.elements.length ? (
                              <div className="mt-1.5 flex flex-wrap gap-1">
                                {page.elements.map((element) => (
                                  <span
                                    key={element}
                                    className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]"
                                  >
                                    {element}
                                  </span>
                                ))}
                              </div>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-muted-foreground">{t("pages.empty")}</p>
                    )}
                  </section>

                  <section className="grid gap-2">
                    <p className="text-sm font-medium">{t("parameters.title")}</p>
                    {current.document.parameters.length ? (
                      <ul className="grid gap-1.5">
                        {current.document.parameters.map((parameter) => (
                          <li key={parameter.name} className="flex items-center gap-2 text-xs">
                            {parameter.secret ? (
                              <Lock className="size-3.5 text-muted-foreground" />
                            ) : (
                              <span className="size-3.5" />
                            )}
                            <span className="font-mono">{parameter.name}</span>
                            <span className="text-muted-foreground">{parameter.type}</span>
                            {parameter.secret ? (
                              <Badge variant="outline" className="font-normal">
                                {t("parameters.secret")}
                              </Badge>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-muted-foreground">{t("parameters.empty")}</p>
                    )}
                  </section>
                </>
              ) : null}

              <section className="grid gap-2">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("details.description")}
                </p>
                <p className="text-sm whitespace-pre-wrap">
                  {external.description || t("details.noDescription")}
                </p>
              </section>
              <section className="grid gap-2">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("details.priority")}
                </p>
                <Badge variant="outline" className="w-fit">
                  {external.priority || t("details.unset")}
                </Badge>
              </section>
              <section className="grid gap-2">
                <p className="text-xs font-medium text-muted-foreground">
                  {t("details.labels")}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {external.labels.length ? (
                    external.labels.map((label) => (
                      <Badge key={label} variant="secondary">
                        {label}
                      </Badge>
                    ))
                  ) : (
                    <span className="text-sm text-muted-foreground">{t("details.unset")}</span>
                  )}
                </div>
              </section>
            </div>
          </ScrollArea>
        </aside>
      </div>
    </div>
  );
}

// ── stages ──────────────────────────────────────────────────────────────────

function ManualStage({
  step,
  model,
  derived,
  onSelectModelStep,
}: {
  step: ManualStep | undefined;
  model: TestModel | null;
  derived: TestModelStep[];
  onSelectModelStep: (id: string) => void;
}) {
  const t = useTranslations("testManagement.workspace.canvas");
  if (!step) {
    return <p className="place-self-center text-sm text-muted-foreground">{t("noSteps")}</p>;
  }
  return (
    <div className="grid gap-5">
      <div className="flex items-start gap-4">
        <div className="grid size-11 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
          <span className="font-mono font-semibold">{step.position}</span>
        </div>
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {t("currentAction")}
          </p>
          <p className="mt-1 text-lg font-semibold">{step.action}</p>
        </div>
      </div>
      {step.data ? (
        <div className="rounded-md border bg-muted/40 p-3">
          <p className="text-xs font-medium text-muted-foreground">{t("data")}</p>
          <p className="mt-1 text-sm whitespace-pre-wrap">{step.data}</p>
        </div>
      ) : null}
      {step.expected ? (
        <div className="flex items-start gap-2 rounded-md border border-primary/20 bg-primary/5 p-3">
          <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-primary" />
          <div>
            <p className="text-xs font-medium text-muted-foreground">{t("expected")}</p>
            <p className="mt-1 text-sm whitespace-pre-wrap">{step.expected}</p>
          </div>
        </div>
      ) : null}

      <div className="grid gap-2 border-t pt-4">
        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {t("modelSteps")}
        </p>
        {!model ? (
          <p className="text-sm text-muted-foreground">{t("notModelled")}</p>
        ) : derived.length === 0 ? (
          <p className="text-sm text-destructive">{t("noModelSteps")}</p>
        ) : (
          <ul className="grid gap-1.5">
            {derived.map((modelStep) => (
              <li key={modelStep.id}>
                <button
                  type="button"
                  className="grid w-full grid-cols-[auto_auto_1fr] items-center gap-2 rounded-md border px-2.5 py-2 text-left text-sm hover:bg-muted/60"
                  onClick={() => onSelectModelStep(modelStep.id)}
                >
                  <span className="font-mono text-xs text-muted-foreground">
                    {modelStep.id}
                  </span>
                  <ActionBadge action={modelStep.action} />
                  <span className="truncate text-xs">
                    {modelStep.description ?? describeTarget(modelStep.target)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function ModelStage({
  step,
  manualSteps,
  onSelectManualStep,
}: {
  step: TestModelStep | undefined;
  manualSteps: ManualStep[];
  onSelectManualStep: (position: number) => void;
}) {
  const t = useTranslations("testManagement.workspace.canvas");
  if (!step) {
    return <p className="place-self-center text-sm text-muted-foreground">{t("noSteps")}</p>;
  }
  const sources = step.sourceStepIds
    .map((id) => manualSteps.find((m) => sourceId(m.position) === id))
    .filter((m): m is ManualStep => Boolean(m));
  return (
    <div className="grid gap-5">
      <div className="flex items-start gap-4">
        <div className="grid size-11 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
          <Workflow className="size-5" />
        </div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{step.id}</span>
            <ActionBadge action={step.action} />
          </div>
          <p className="mt-1 text-lg font-semibold">
            {step.description ?? describeTarget(step.target)}
          </p>
        </div>
      </div>

      <dl className="grid gap-2 sm:grid-cols-2">
        {step.target ? (
          <Fact label={t("target")} value={describeTarget(step.target)} mono />
        ) : null}
        {step.to ? <Fact label={t("to")} value={describeTarget(step.to)} mono /> : null}
        {step.value ? <ValueFact value={step.value} /> : null}
        {step.assertion ? (
          <Fact
            label={t("assertion")}
            value={
              <span className="flex flex-wrap items-center gap-1.5">
                <Badge variant="secondary" className="font-mono font-normal">
                  {step.assertion.condition}
                </Badge>
                {step.assertion.attribute ? (
                  <span className="text-xs text-muted-foreground">
                    {t("attribute")}:{" "}
                    <span className="font-mono">{step.assertion.attribute}</span>
                  </span>
                ) : null}
                {step.assertion.expected !== undefined ? (
                  <span className="text-xs text-muted-foreground">
                    {t("expectedValue")}:{" "}
                    <span className="font-mono">{String(step.assertion.expected)}</span>
                  </span>
                ) : null}
              </span>
            }
          />
        ) : null}
        {step.flow ? <Fact label={t("flow")} value={step.flow} mono /> : null}
      </dl>

      <div className="grid gap-2 border-t pt-4">
        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {t("sourceSteps")}
        </p>
        {step.derived && sources.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("derived")}</p>
        ) : (
          <ul className="grid gap-1.5">
            {sources.map((manual) => (
              <li key={manual.position}>
                <button
                  type="button"
                  className="grid w-full grid-cols-[1.75rem_1fr] items-start gap-2 rounded-md border px-2.5 py-2 text-left text-sm hover:bg-muted/60"
                  onClick={() => onSelectManualStep(manual.position)}
                >
                  <span className="font-mono text-xs text-muted-foreground">
                    {manual.position}
                  </span>
                  <span className="line-clamp-2">{manual.action}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function ValueFact({ value }: { value: TestModelValue }) {
  const t = useTranslations("testManagement.workspace.canvas");
  if (value.kind === "literal") {
    return <Fact label={t("value")} value={String(value.value)} mono hint={t("literal")} />;
  }
  return (
    <Fact
      label={t("value")}
      value={
        <span className="flex items-center gap-1.5">
          {value.kind === "secret" ? <Lock className="size-3.5 text-muted-foreground" /> : null}
          <span className="font-mono">{value.name}</span>
        </span>
      }
      hint={value.kind === "secret" ? t("secret") : t("param")}
    />
  );
}

function Fact({
  label,
  value,
  hint,
  mono,
}: {
  label: string;
  value: React.ReactNode;
  hint?: string;
  mono?: boolean;
}) {
  return (
    <div className="rounded-md border bg-muted/30 p-2.5">
      <dt className="flex items-center justify-between text-xs font-medium text-muted-foreground">
        {label}
        {hint ? <span className="font-normal">{hint}</span> : null}
      </dt>
      <dd className={`mt-1 text-sm ${mono ? "font-mono" : ""}`}>{value}</dd>
    </div>
  );
}

// ── the 422s ────────────────────────────────────────────────────────────────

/**
 * A refusal is a result, not a failure: the questions go next to the step they are about, with
 * the way to answer them one click away. Anything else falls through to the ordinary error state.
 */
function ModellingProblem({ error, editHref }: { error: unknown; editHref?: string }) {
  const t = useTranslations("testManagement.workspace.problems");
  const apiError = error instanceof ApiError ? error : undefined;
  const questions = apiError?.problem?.questions;
  const violations = apiError?.problem?.violations;

  if (apiError?.code === "test-case-ambiguous" && questions) {
    return (
      <Alert>
        <Sparkles className="size-4" />
        <AlertTitle>{t("ambiguous")}</AlertTitle>
        <AlertDescription>
          <p>{t("ambiguousHint")}</p>
          <ul className="mt-2 grid gap-1.5">
            {questions.map((question, index) => (
              <li key={index} className="flex gap-2 text-sm">
                <Badge variant="outline" className="shrink-0 font-mono font-normal">
                  {question.sourceStepId
                    ? t("step", { id: question.sourceStepId })
                    : t("wholeCase")}
                </Badge>
                <span>{question.question}</span>
              </li>
            ))}
          </ul>
          {editHref ? (
            <Button asChild variant="outline" size="sm" className="mt-3 w-fit">
              <Link href={editHref}>{t("editCase")}</Link>
            </Button>
          ) : null}
        </AlertDescription>
      </Alert>
    );
  }

  if (apiError?.code === "test-model-invalid" && violations) {
    return (
      <Alert variant="destructive">
        <AlertTitle>{t("invalid")}</AlertTitle>
        <AlertDescription>
          <p>{t("invalidHint")}</p>
          <ul className="mt-2 grid gap-1 font-mono text-xs">
            {violations.map((violation, index) => (
              <li key={index}>
                <span className="text-muted-foreground">{violation.path}</span>{" "}
                {violation.message}
              </li>
            ))}
          </ul>
        </AlertDescription>
      </Alert>
    );
  }

  return <ErrorState error={error} />;
}

// ── small parts ─────────────────────────────────────────────────────────────

function EmptyRail({
  icon: Icon,
  title,
  hint,
}: {
  icon: typeof Code2;
  title: string;
  hint: string;
}) {
  return (
    <div className="p-4">
      <div className="grid justify-items-center gap-3 rounded-md border border-dashed px-4 py-10 text-center">
        <Icon className="size-6 text-muted-foreground" />
        <div>
          <p className="text-sm font-medium">{title}</p>
          <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
        </div>
      </div>
    </div>
  );
}

function ActionBadge({ action }: { action: TestModelStep["action"] }) {
  const variant = action === "assert" || action === "waitFor" ? "default" : "secondary";
  return (
    <Badge variant={variant} className="font-mono font-normal">
      {action}
    </Badge>
  );
}

function PipelineItem({ done, label }: { done?: boolean; label: string }) {
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className={`size-2 rounded-full ${done ? "bg-primary" : "border bg-background"}`} />
      <span className={done ? "text-foreground" : "text-muted-foreground"}>{label}</span>
    </div>
  );
}

// ── helpers ─────────────────────────────────────────────────────────────────

function sourceId(position: number) {
  return `${SOURCE_PREFIX}${position}`;
}

/**
 * Once imported, the Specra case is the automation input — its steps are what the coverage refers
 * to, and what the tester edits to answer a question. Before that, Xray's steps are all there is.
 */
function manualStepsOf(external: ExternalTestDetail, testCase: TestCase | null): ManualStep[] {
  if (testCase) {
    return testCase.steps.map((step) => ({
      position: step.position,
      action: step.action,
      data: step.data,
      expected: step.expected,
    }));
  }
  return external.steps.map((step) => ({
    position: step.position,
    action: step.action,
    data: step.data,
    expected: step.expected,
  }));
}

function modelStepsOf(model: TestModel): { step: TestModelStep; section: Section }[] {
  const { setup, steps, teardown } = model.document;
  return [
    ...setup.map((step) => ({ step, section: "setup" as const })),
    ...steps.map((step) => ({ step, section: "steps" as const })),
    ...teardown.map((step) => ({ step, section: "teardown" as const })),
  ];
}

function describeTarget(target: TestModelTarget | undefined) {
  if (!target) {
    return "";
  }
  if (target.selector) {
    return `${target.selector.strategy}: ${target.selector.value}`;
  }
  return target.element ? `${target.page}.${target.element}` : (target.page ?? "");
}
