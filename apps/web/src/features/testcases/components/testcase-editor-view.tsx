"use client";

import { ArrowLeft, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useDeleteTestCase,
  useTestCase,
  useUpdateTestCase,
} from "@/features/testcases/api/testcases";
import { OutOfDateBadge, StatusBadge } from "@/features/testcases/components/testcase-badges";
import { TestCaseForm } from "@/features/testcases/components/testcase-form";
import type { TestCaseFormValues } from "@/features/testcases/schemas";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { TestCase } from "@/lib/api/types";

export function TestCaseEditorView({
  projectId,
  testCaseId,
}: {
  projectId: string;
  testCaseId: string;
}) {
  const t = useTranslations("testcases");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");
  const format = useFormatter();
  const router = useRouter();

  const testCase = useTestCase(testCaseId);
  const updateTestCase = useUpdateTestCase(testCaseId);
  const deleteTestCase = useDeleteTestCase();
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  if (testCase.isPending) {
    return (
      <div className="grid max-w-4xl gap-4">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (testCase.isError) {
    return (
      <div className="grid max-w-4xl gap-4">
        <BackLink projectId={projectId} label={t("editor.back")} />
        <ErrorState error={testCase.error} onRetry={() => void testCase.refetch()} />
      </div>
    );
  }

  const current = testCase.data;

  function save(values: TestCaseFormValues) {
    updateTestCase.mutate(
      {
        title: values.title,
        description: values.description || undefined,
        preconditions: values.preconditions || undefined,
        expectedResult: values.expectedResult || undefined,
        priority: values.priority,
        steps: values.steps.map((step) => ({
          action: step.action,
          expected: step.expected || undefined,
        })),
        tags: values.tags,
      },
      {
        onSuccess: (saved) => toast.success(t("toast.saved", { reference: saved.reference })),
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  function confirmDelete() {
    deleteTestCase.mutate(current.id, {
      onSuccess: () => {
        toast.success(t("toast.deleted", { reference: current.reference }));
        router.push(`/projects/${projectId}`);
      },
      onError: (error) => toast.error(describe(error)),
      onSettled: () => setConfirmingDelete(false),
    });
  }

  return (
    <div className="grid max-w-4xl gap-6">
      <div className="grid gap-3">
        <BackLink projectId={projectId} label={t("editor.back")} />
        <div className="flex flex-wrap items-center gap-3">
          <span className="font-mono text-sm font-semibold">{current.reference}</span>
          <StatusBadge status={current.automationStatus} />
          {current.outOfDate ? <OutOfDateBadge /> : null}
          <Badge variant="outline" className="font-normal text-muted-foreground">
            {t("editor.updated", {
              when: format.dateTime(new Date(current.updatedAt), {
                dateStyle: "medium",
                timeStyle: "short",
              }),
            })}
          </Badge>
          <div className="ms-auto">
            <Button
              variant="outline"
              size="sm"
              className="text-destructive"
              onClick={() => setConfirmingDelete(true)}
            >
              <Trash2 className="size-4" />
              {tActions("delete")}
            </Button>
          </div>
        </div>
      </div>

      <TestCaseForm
        defaultValues={toFormValues(current)}
        submitLabel={tActions("save")}
        pending={updateTestCase.isPending}
        serverErrors={
          updateTestCase.error instanceof ApiError
            ? updateTestCase.error.fieldErrors
            : undefined
        }
        onSubmit={save}
      />

      <AlertDialog open={confirmingDelete} onOpenChange={setConfirmingDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDelete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDelete.description", {
                reference: current.reference,
                title: current.title,
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction disabled={deleteTestCase.isPending} onClick={confirmDelete}>
              {t("confirmDelete.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function BackLink({ projectId, label }: { projectId: string; label: string }) {
  return (
    <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
      <Link href={`/projects/${projectId}`}>
        <ArrowLeft className="size-4" />
        {label}
      </Link>
    </Button>
  );
}

function toFormValues(testCase: TestCase): TestCaseFormValues {
  return {
    title: testCase.title,
    description: testCase.description ?? "",
    preconditions: testCase.preconditions ?? "",
    expectedResult: testCase.expectedResult ?? "",
    priority: testCase.priority,
    steps: testCase.steps.map((step) => ({
      action: step.action,
      expected: step.expected ?? "",
    })),
    tags: testCase.tags,
  };
}
