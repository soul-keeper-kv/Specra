"use client";

import { useForm } from "@tanstack/react-form";
import { Building2, KeyRound, Loader2, Plus, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
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
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCreateGitCredential,
  useDeleteGitCredential,
  useGitCredentials,
} from "@/features/git/api/credentials";
import { buildCredentialSchema, type CredentialFormValues } from "@/features/git/schemas";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { GitCredential } from "@/lib/api/types";

/**
 * Tokens for reaching Git remotes. Workspace-level rather than per project, because one token
 * usually unlocks several repositories — and write-only: the API answers "set", never the value.
 */
export function GitCredentialSettings() {
  const t = useTranslations("settings.git");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");
  const format = useFormatter();

  const active = useActiveWorkspace();
  const credentials = useGitCredentials(active.workspace?.id);
  const create = useCreateGitCredential(active.workspace?.id);
  const remove = useDeleteGitCredential(active.workspace?.id);

  const [open, setOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<GitCredential | null>(null);

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  if (active.isPending || (active.workspace && credentials.isPending)) {
    return (
      <div className="grid max-w-2xl gap-4">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (active.isError) {
    return <ErrorState error={active.error} onRetry={() => void active.refetch()} />;
  }

  if (!active.workspace) {
    return (
      <EmptyState
        icon={Building2}
        title={t("noWorkspace.title")}
        description={t("noWorkspace.description")}
        action={
          <Button asChild size="sm">
            <Link href="/projects">{t("noWorkspace.cta")}</Link>
          </Button>
        }
      />
    );
  }

  if (credentials.isError) {
    return <ErrorState error={credentials.error} onRetry={() => void credentials.refetch()} />;
  }

  const rows = credentials.data ?? [];

  return (
    <div className="grid max-w-2xl gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h2 className="font-heading text-lg font-semibold">{t("title")}</h2>
          <p className="text-sm text-muted-foreground">{t("description")}</p>
        </div>
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus className="size-4" />
          {t("add")}
        </Button>
      </div>

      {rows.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title={t("empty.title")}
          description={t("empty.description")}
          action={
            <Button size="sm" onClick={() => setOpen(true)}>
              <Plus className="size-4" />
              {t("add")}
            </Button>
          }
        />
      ) : (
        <ul className="grid divide-y rounded-lg border">
          {rows.map((credential) => (
            <li key={credential.id} className="flex items-center gap-3 p-3">
              <KeyRound className="size-4 shrink-0 text-muted-foreground" />
              <div className="grid min-w-0 flex-1 gap-0.5">
                <span className="truncate text-sm font-medium">{credential.name}</span>
                <span className="truncate text-xs text-muted-foreground">
                  {credential.username
                    ? t("row.user", { username: credential.username })
                    : t("row.noUser")}
                  {" · "}
                  {format.dateTime(new Date(credential.updatedAt), { dateStyle: "medium" })}
                </span>
              </div>
              {credential.tokenSet ? (
                <Badge variant="secondary" className="font-normal">
                  {t("row.tokenStored")}
                </Badge>
              ) : null}
              <Button
                variant="ghost"
                size="icon"
                className="size-8 text-muted-foreground hover:text-destructive"
                aria-label={t("row.delete", { name: credential.name })}
                onClick={() => setPendingDelete(credential)}
              >
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t("form.title")}</DialogTitle>
            <DialogDescription>{t("form.description")}</DialogDescription>
          </DialogHeader>
          <CredentialForm
            submitLabel={t("form.submit")}
            pending={create.isPending}
            serverErrors={
              create.error instanceof ApiError ? create.error.fieldErrors : undefined
            }
            onSubmit={(values) =>
              create.mutate(
                {
                  name: values.name,
                  username: values.username || undefined,
                  token: values.token,
                },
                {
                  onSuccess: (credential) => {
                    toast.success(t("toast.created", { name: credential.name }));
                    setOpen(false);
                  },
                  onError: (error) => toast.error(describe(error)),
                },
              )
            }
          />
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(next) => (next ? null : setPendingDelete(null))}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDelete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDelete.description", { name: pendingDelete?.name ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={remove.isPending}
              onClick={() =>
                pendingDelete &&
                remove.mutate(pendingDelete.id, {
                  onSuccess: () =>
                    toast.success(t("toast.deleted", { name: pendingDelete.name })),
                  onError: (error) => toast.error(describe(error)),
                  onSettled: () => setPendingDelete(null),
                })
              }
            >
              {t("confirmDelete.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function CredentialForm({
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: {
  submitLabel: string;
  pending?: boolean;
  serverErrors?: Record<string, string>;
  onSubmit: (values: CredentialFormValues) => void;
}) {
  const t = useTranslations("settings.git.form");
  const tValidation = useTranslations("git.validation");

  const schema = useMemo(() => buildCredentialSchema(tValidation), [tValidation]);

  const form = useForm({
    defaultValues: { name: "", username: "", token: "" } as CredentialFormValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => onSubmit(value),
  });

  return (
    <form
      className="grid gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        event.stopPropagation();
        void form.handleSubmit();
      }}
    >
      <form.Field name="name">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("name")}</Label>
            <Input
              id={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("namePlaceholder")}
              aria-invalid={field.state.meta.errors.length > 0 || Boolean(serverErrors?.name)}
            />
            <FieldError messages={field.state.meta.errors} serverMessage={serverErrors?.name} />
          </div>
        )}
      </form.Field>

      <form.Field name="username">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("username")}</Label>
            <Input
              id={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("usernamePlaceholder")}
            />
            <p className="text-xs text-muted-foreground">{t("usernameHint")}</p>
          </div>
        )}
      </form.Field>

      <form.Field name="token">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("token")}</Label>
            <Input
              id={field.name}
              type="password"
              autoComplete="off"
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder="ghp_…"
              aria-invalid={field.state.meta.errors.length > 0 || Boolean(serverErrors?.token)}
            />
            <p className="text-xs text-muted-foreground">{t("tokenHint")}</p>
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.token}
            />
          </div>
        )}
      </form.Field>

      <form.Subscribe selector={(state) => state.canSubmit}>
        {(canSubmit) => (
          <Button type="submit" disabled={!canSubmit || pending}>
            {pending ? <Loader2 className="size-4 animate-spin" /> : null}
            {submitLabel}
          </Button>
        )}
      </form.Subscribe>
    </form>
  );
}

function FieldError({
  messages,
  serverMessage,
}: {
  messages: unknown[];
  serverMessage?: string;
}) {
  const text =
    serverMessage ??
    messages
      .map((error) =>
        typeof error === "string" ? error : (error as { message?: string })?.message,
      )
      .filter(Boolean)
      .join(", ");

  if (!text) return null;
  return <p className="text-sm text-destructive">{text}</p>;
}
