"use client";

import type { SortingState } from "@tanstack/react-table";
import { ClipboardList, Loader2, Plus, Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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
import {
  useCreateTestCase,
  useDeleteTestCase,
  useTestCases,
} from "@/features/testcases/api/testcases";
import { TestCasesTable } from "@/features/testcases/components/testcases-table";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useRouter } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { AutomationStatus, TestCasePriority, TestCaseSummary } from "@/lib/api/types";

const PAGE_SIZE = 20;

const STATUSES: AutomationStatus[] = ["NOT_AUTOMATED", "MODELLED", "GENERATED", "COMMITTED"];
const PRIORITIES: TestCasePriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

/** Radix's Select cannot carry an empty-string value, so "no filter" needs its own token. */
const ALL = "ALL";

export function TestCasesView({ projectId }: { projectId: string }) {
  const t = useTranslations("testcases");
  const tStatus = useTranslations("testcases.status");
  const tPriority = useTranslations("testcases.priority");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");
  const router = useRouter();

  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "updatedAt", desc: true }]);
  const [createOpen, setCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newPriority, setNewPriority] = useState<TestCasePriority>("MEDIUM");
  const [pendingDelete, setPendingDelete] = useState<TestCaseSummary | null>(null);

  const debouncedSearch = useDebouncedValue(search);

  const sort = sorting[0]
    ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
    : "updatedAt,desc";

  const testCases = useTestCases(projectId, {
    q: debouncedSearch || undefined,
    status: status === ALL ? undefined : (status as AutomationStatus),
    page,
    size: PAGE_SIZE,
    sort,
  });
  const createTestCase = useCreateTestCase(projectId);
  const deleteTestCase = useDeleteTestCase();

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  function submitCreate() {
    const title = newTitle.trim();
    if (!title) return;
    createTestCase.mutate(
      { title, priority: newPriority, steps: [], tags: [] },
      {
        onSuccess: (testCase) => {
          toast.success(t("toast.created", { reference: testCase.reference }));
          setCreateOpen(false);
          // Straight into the editor: a case without steps is a stub, not a deliverable.
          router.push(`/projects/${projectId}/test-cases/${testCase.id}`);
        },
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  function confirmDelete(testCase: TestCaseSummary) {
    deleteTestCase.mutate(testCase.id, {
      onSuccess: () => toast.success(t("toast.deleted", { reference: testCase.reference })),
      onError: (error) => toast.error(describe(error)),
      onSettled: () => setPendingDelete(null),
    });
  }

  const isEmpty = testCases.data?.content.length === 0;
  const unfiltered = !debouncedSearch && status === ALL;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-heading text-lg font-semibold">{t("title")}</h2>
        <Dialog
          open={createOpen}
          onOpenChange={(open) => {
            setCreateOpen(open);
            if (open) {
              // Reset in the event that opens the dialog, not in an effect after it rendered.
              setNewTitle("");
              setNewPriority("MEDIUM");
            }
          }}
        >
          <DialogTrigger asChild>
            <Button>
              <Plus className="size-4" />
              {t("new.trigger")}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>{t("new.title")}</DialogTitle>
              <DialogDescription>{t("new.description")}</DialogDescription>
            </DialogHeader>
            <form
              className="grid gap-4"
              onSubmit={(event) => {
                event.preventDefault();
                submitCreate();
              }}
            >
              <div className="grid gap-2">
                <Label htmlFor="new-testcase-title">{t("form.title")}</Label>
                <Input
                  id="new-testcase-title"
                  value={newTitle}
                  onChange={(event) => setNewTitle(event.target.value)}
                  placeholder={t("form.titlePlaceholder")}
                  maxLength={200}
                />
              </div>
              <div className="grid gap-2">
                <Label>{t("form.priority")}</Label>
                <Select
                  value={newPriority}
                  onValueChange={(value) => setNewPriority(value as TestCasePriority)}
                >
                  <SelectTrigger className="w-48">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PRIORITIES.map((priority) => (
                      <SelectItem key={priority} value={priority}>
                        {tPriority(priority)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={!newTitle.trim() || createTestCase.isPending}>
                  {createTestCase.isPending ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : null}
                  {t("new.submit")}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative max-w-sm flex-1 basis-64">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
            placeholder={t("searchPlaceholder")}
            aria-label={tActions("search")}
            className="pl-9"
          />
        </div>
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-48" aria-label={t("filter.status")}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{t("filter.allStatuses")}</SelectItem>
            {STATUSES.map((value) => (
              <SelectItem key={value} value={value}>
                {tStatus(value)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {testCases.isPending ? (
        <div className="grid gap-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-12 w-full" />
          ))}
        </div>
      ) : testCases.isError ? (
        <ErrorState error={testCases.error} onRetry={() => void testCases.refetch()} />
      ) : isEmpty ? (
        <EmptyState
          icon={ClipboardList}
          title={unfiltered ? t("table.empty") : t("table.noMatch")}
          description={unfiltered ? t("table.emptyHint") : t("table.noMatchHint")}
          action={
            unfiltered ? (
              <Button size="sm" onClick={() => setCreateOpen(true)}>
                <Plus className="size-4" />
                {t("new.trigger")}
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <TestCasesTable
            testCases={testCases.data.content}
            sorting={sorting}
            onSortingChange={setSorting}
            onDelete={setPendingDelete}
          />

          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {t("count", { count: testCases.data.totalElements })}
              {testCases.isFetching ? (
                <Loader2 className="ml-2 inline size-3 animate-spin" />
              ) : null}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={testCases.data.first}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                {tActions("previous")}
              </Button>
              <span className="text-muted-foreground tabular-nums">
                {t("pagination", {
                  page: testCases.data.page + 1,
                  total: Math.max(1, testCases.data.totalPages),
                })}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={testCases.data.last}
                onClick={() => setPage((current) => current + 1)}
              >
                {tActions("next")}
              </Button>
            </div>
          </div>
        </>
      )}

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => (open ? null : setPendingDelete(null))}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDelete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDelete.description", {
                reference: pendingDelete?.reference ?? "",
                title: pendingDelete?.title ?? "",
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={deleteTestCase.isPending}
              onClick={() => pendingDelete && confirmDelete(pendingDelete)}
            >
              {t("confirmDelete.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
