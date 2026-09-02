"use client";

import { Globe, KeyRound, Pencil, Plus, Server, Trash2 } from "lucide-react";
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
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCreateEnvironment,
  useDeleteEnvironment,
  useEnvironments,
  useUpdateEnvironment,
} from "@/features/environments/api/environments";
import { EnvironmentForm } from "@/features/environments/components/environment-form";
import type { EnvironmentFormValues } from "@/features/environments/schemas";
import { ApiError } from "@/lib/api/client";
import type { Environment, EnvironmentInput } from "@/lib/api/types";

/**
 * Where a project's runs point, and what they carry.
 *
 * Without a row here a run has nothing to target, so this screen is what stands between a
 * committed spec and an execution — which is why it lives beside Runs rather than in settings.
 */
export function EnvironmentsView({ projectId }: { projectId: string }) {
  const t = useTranslations("environments");

  const environments = useEnvironments(projectId);
  const create = useCreateEnvironment(projectId);
  const update = useUpdateEnvironment(projectId);
  const remove = useDeleteEnvironment(projectId);

  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Environment | null>(null);
  const [pendingDelete, setPendingDelete] = useState<Environment | null>(null);

  function submitCreate(values: EnvironmentFormValues) {
    create.mutate(toInput(values), {
      onSuccess: (environment) => {
        setCreateOpen(false);
        toast.success(t("toast.created", { name: environment.name }));
      },
      onError: (error) => toast.error(describe(error, t)),
    });
  }

  function submitUpdate(id: string, values: EnvironmentFormValues) {
    update.mutate(
      { id, input: toInput(values) },
      {
        onSuccess: (environment) => {
          setEditing(null);
          toast.success(t("toast.updated", { name: environment.name }));
        },
        onError: (error) => toast.error(describe(error, t)),
      },
    );
  }

  function confirmDelete() {
    if (!pendingDelete) return;
    const name = pendingDelete.name;
    remove.mutate(pendingDelete.id, {
      onSuccess: () => {
        setPendingDelete(null);
        toast.success(t("toast.deleted", { name }));
      },
      onError: (error) => toast.error(describe(error, t)),
    });
  }

  if (environments.isPending) {
    return <Skeleton className="m-4 h-64" />;
  }
  if (environments.isError) {
    return (
      <ErrorState error={environments.error} onRetry={() => void environments.refetch()} />
    );
  }

  const rows = environments.data ?? [];

  return (
    <div className="grid gap-4 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">{t("subtitle")}</p>

        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <Plus className="size-4" />
              {t("new.action")}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-2xl">
            <DialogHeader>
              <DialogTitle>{t("new.title")}</DialogTitle>
              <DialogDescription>{t("new.description")}</DialogDescription>
            </DialogHeader>
            <EnvironmentForm
              submitLabel={t("new.submit")}
              pending={create.isPending}
              serverErrors={
                create.error instanceof ApiError ? create.error.fieldErrors : undefined
              }
              onSubmit={submitCreate}
            />
          </DialogContent>
        </Dialog>
      </div>

      {rows.length === 0 ? (
        <EmptyState
          icon={Server}
          title={t("empty.title")}
          description={t("empty.description")}
        />
      ) : (
        <ul className="grid gap-2">
          {rows.map((environment) => (
            <EnvironmentRow
              key={environment.id}
              environment={environment}
              onEdit={() => setEditing(environment)}
              onDelete={() => setPendingDelete(environment)}
            />
          ))}
        </ul>
      )}

      {/* Keyed on the id so switching rows rebuilds the form rather than reusing its state. */}
      <Dialog open={editing !== null} onOpenChange={(open) => (open ? null : setEditing(null))}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("edit.title")}</DialogTitle>
            <DialogDescription>{t("edit.description")}</DialogDescription>
          </DialogHeader>
          {editing ? (
            <EnvironmentForm
              key={editing.id}
              environment={editing}
              submitLabel={t("edit.submit")}
              pending={update.isPending}
              serverErrors={
                update.error instanceof ApiError ? update.error.fieldErrors : undefined
              }
              onSubmit={(values) => submitUpdate(editing.id, values)}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => (open ? null : setPendingDelete(null))}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("delete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("delete.description", { name: pendingDelete?.name ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("delete.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={remove.isPending}
              onClick={(event) => {
                // The dialog closes on click by default; the mutation decides when it may.
                event.preventDefault();
                confirmDelete();
              }}
            >
              {t("delete.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function EnvironmentRow({
  environment,
  onEdit,
  onDelete,
}: {
  environment: Environment;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const t = useTranslations("environments");
  const secrets = environment.variables.filter((variable) => variable.secret).length;

  return (
    <li className="grid gap-2 rounded-lg border p-3 sm:grid-cols-[1fr_auto] sm:items-center">
      <div className="grid gap-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm font-medium">{environment.name}</span>
          {environment.isDefault ? <Badge variant="secondary">{t("default")}</Badge> : null}
        </span>
        <span className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <Globe className="size-3" />
            {environment.baseUrl}
          </span>
          <span>{t("variableCount", { count: environment.variables.length })}</span>
          {secrets > 0 ? (
            <span className="flex items-center gap-1">
              <KeyRound className="size-3" />
              {t("secretCount", { count: secrets })}
            </span>
          ) : null}
        </span>
      </div>

      <div className="flex gap-1">
        <Button variant="ghost" size="icon" onClick={onEdit} aria-label={t("edit.action")}>
          <Pencil className="size-4" />
        </Button>
        <Button variant="ghost" size="icon" onClick={onDelete} aria-label={t("delete.action")}>
          <Trash2 className="size-4" />
        </Button>
      </div>
    </li>
  );
}

/**
 * Form values into a request body.
 *
 * A stored secret left untouched is sent with no `value` at all, which is how the API is told to
 * keep the one it has — an empty string would be a request to store an empty secret.
 */
function toInput(values: EnvironmentFormValues): EnvironmentInput {
  return {
    name: values.name.trim(),
    baseUrl: values.baseUrl.trim(),
    isDefault: values.isDefault,
    variables: values.variables.map((variable) => ({
      key: variable.key.trim(),
      secret: variable.secret,
      ...(variable.secret && variable.value.trim() === "" ? {} : { value: variable.value }),
    })),
  };
}

/** Branches on `code`, never on the message — the message is translated per request. */
function describe(
  error: unknown,
  t: ReturnType<typeof useTranslations<"environments">>,
): string {
  if (error instanceof ApiError) {
    if (error.code === "conflict") {
      return error.problem?.detail ?? t("problems.conflict");
    }
    if (error.code === "forbidden") {
      return t("problems.forbidden");
    }
  }
  return t("problems.generic");
}
