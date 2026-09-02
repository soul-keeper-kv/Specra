"use client";

import { Check, Layers, Loader2, Pencil, ScanSearch, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useInspectPage, usePageObjects, useUpdateElement } from "@/features/pages/api/pages";
import { ApiError } from "@/lib/api/client";
import type { LocatorStrategy, PageElement, PageObject } from "@/lib/api/types";

/** The API's order is the priority order, and the select relies on it. */
const STRATEGIES: LocatorStrategy[] = [
  "TEST_ID",
  "ROLE",
  "LABEL",
  "PLACEHOLDER",
  "TEXT",
  "ALT_TEXT",
  "TITLE",
  "CSS",
  "XPATH",
];

/**
 * What the application's pages contain, as inspection read them.
 *
 * A page with no locators is what makes a generation unresolvable, so this screen is where that
 * gets fixed — and the confidence beside each locator is what lets a person distrust a weak one
 * before it flakes rather than after.
 */
export function PagesView({ projectId }: { projectId: string }) {
  const t = useTranslations("pages");
  const pages = usePageObjects(projectId);
  const inspect = useInspectPage(projectId);

  const [open, setOpen] = useState(false);
  const [pageName, setPageName] = useState("");
  const [route, setRoute] = useState("");

  function runInspection() {
    inspect.mutate(
      { pageName: pageName.trim(), route: route.trim() },
      {
        onSuccess: (page) => {
          setOpen(false);
          setPageName("");
          setRoute("");
          toast.success(t("toast.inspected", { count: page.elements.length }));
        },
        onError: (error) => toast.error(describe(error, t)),
      },
    );
  }

  if (pages.isPending) {
    return <Skeleton className="m-4 h-64" />;
  }
  if (pages.isError) {
    return <ErrorState error={pages.error} onRetry={() => void pages.refetch()} />;
  }

  const rows = pages.data ?? [];

  return (
    <div className="grid gap-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>

        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <ScanSearch className="size-4" />
              {t("inspect.action")}
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t("inspect.title")}</DialogTitle>
              <DialogDescription>{t("inspect.description")}</DialogDescription>
            </DialogHeader>

            <div className="grid gap-4">
              <div className="grid gap-2">
                <Label htmlFor="page-name">{t("inspect.pageName")}</Label>
                <Input
                  id="page-name"
                  value={pageName}
                  onChange={(event) => setPageName(event.target.value)}
                  placeholder={t("inspect.pageNamePlaceholder")}
                  className="font-mono"
                />
                <p className="text-xs text-muted-foreground">{t("inspect.pageNameHint")}</p>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="page-route">{t("inspect.route")}</Label>
                <Input
                  id="page-route"
                  value={route}
                  onChange={(event) => setRoute(event.target.value)}
                  placeholder={t("inspect.routePlaceholder")}
                  className="font-mono"
                />
                <p className="text-xs text-muted-foreground">{t("inspect.routeHint")}</p>
              </div>

              <Button
                disabled={inspect.isPending || !pageName.trim() || !route.trim()}
                onClick={runInspection}
              >
                {inspect.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <ScanSearch className="size-4" />
                )}
                {inspect.isPending ? t("inspect.running") : t("inspect.submit")}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {rows.length === 0 ? (
        <EmptyState
          icon={Layers}
          title={t("empty.title")}
          description={t("empty.description")}
        />
      ) : (
        <ul className="grid gap-3">
          {rows.map((page) => (
            <PageRow key={page.id} page={page} projectId={projectId} />
          ))}
        </ul>
      )}
    </div>
  );
}

function PageRow({ page, projectId }: { page: PageObject; projectId: string }) {
  const t = useTranslations("pages");
  const format = useFormatter();

  return (
    <li className="grid gap-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-sm font-medium">{page.name}</span>
        {page.route ? (
          <span className="font-mono text-xs text-muted-foreground">{page.route}</span>
        ) : null}
        {page.inspectedAt ? (
          <span className="text-xs text-muted-foreground">
            {t("inspectedAt", { when: format.relativeTime(new Date(page.inspectedAt)) })}
          </span>
        ) : (
          // The state that blocks a generation, said plainly rather than left to be inferred
          // from an empty element list.
          <Badge variant="outline">{t("neverInspected")}</Badge>
        )}
      </div>

      {page.elements.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t("noElements")}</p>
      ) : (
        <ul className="grid gap-1">
          {page.elements.map((element) => (
            <ElementRow
              key={element.id}
              pageId={page.id}
              projectId={projectId}
              element={element}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

function ElementRow({
  pageId,
  projectId,
  element,
}: {
  pageId: string;
  projectId: string;
  element: PageElement;
}) {
  const t = useTranslations("pages");
  const update = useUpdateElement(projectId);

  const [editing, setEditing] = useState(false);
  const [strategy, setStrategy] = useState<LocatorStrategy>(element.strategy);
  const [value, setValue] = useState(element.value);
  const [qualifier, setQualifier] = useState(element.qualifier ?? "");

  // A number, not a string — the API sends a decimal so it does not lose precision in JSON.
  const confidence = element.confidence === null ? null : Number(element.confidence);

  function save() {
    update.mutate(
      {
        pageId,
        name: element.name,
        input: {
          strategy,
          value: value.trim(),
          ...(strategy === "ROLE" && qualifier.trim() ? { qualifier: qualifier.trim() } : {}),
        },
      },
      {
        onSuccess: () => {
          setEditing(false);
          toast.success(t("toast.updated", { name: element.name }));
        },
        onError: (error) => toast.error(describe(error, t)),
      },
    );
  }

  if (editing) {
    return (
      <li className="grid gap-2 rounded-md border bg-muted/30 p-2">
        <span className="font-mono text-xs font-medium">{element.name}</span>
        <div className="grid gap-2 sm:grid-cols-[10rem_minmax(0,1fr)_auto] sm:items-center">
          <Select
            value={strategy}
            onValueChange={(next) => setStrategy(next as LocatorStrategy)}
          >
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STRATEGIES.map((option) => (
                <SelectItem key={option} value={option}>
                  {t(`strategy.${option}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Input
            value={value}
            onChange={(event) => setValue(event.target.value)}
            className="h-8 font-mono text-xs"
            aria-label={t("locatorValue")}
          />

          <div className="flex gap-1">
            <Button size="icon" className="size-8" disabled={update.isPending} onClick={save}>
              {update.isPending ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <Check className="size-3.5" />
              )}
            </Button>
            <Button
              size="icon"
              variant="ghost"
              className="size-8"
              onClick={() => setEditing(false)}
              aria-label={t("cancel")}
            >
              <X className="size-3.5" />
            </Button>
          </div>
        </div>

        {/* Only a role needs one, so only a role offers the field. */}
        {strategy === "ROLE" ? (
          <Input
            value={qualifier}
            onChange={(event) => setQualifier(event.target.value)}
            placeholder={t("qualifierPlaceholder")}
            className="h-8 text-xs"
            aria-label={t("qualifier")}
          />
        ) : null}
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-2 rounded-md px-2 py-1 hover:bg-muted/40">
      <span className="font-mono text-xs font-medium">{element.name}</span>
      <Badge variant="secondary" className="font-normal">
        {t(`strategy.${element.strategy}`)}
      </Badge>
      <span className="truncate font-mono text-xs text-muted-foreground">
        {element.value}
        {element.qualifier ? ` · ${element.qualifier}` : ""}
      </span>

      {/* Below half, the planner was largely inferring — said here rather than discovered when
          the test flakes. */}
      {confidence !== null && confidence < 0.5 ? (
        <Badge variant="outline" className="font-normal text-destructive">
          {t("weak")}
        </Badge>
      ) : null}

      <Button
        size="icon"
        variant="ghost"
        className="ms-auto size-7"
        onClick={() => setEditing(true)}
        aria-label={t("edit")}
      >
        <Pencil className="size-3.5" />
      </Button>
    </li>
  );
}

/** Branches on `code`, never on the message — the message is translated per request. */
function describe(error: unknown, t: ReturnType<typeof useTranslations<"pages">>): string {
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
