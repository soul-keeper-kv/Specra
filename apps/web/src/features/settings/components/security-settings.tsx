"use client";

import { useForm } from "@tanstack/react-form";
import { KeyRound, Loader2, LogOut, Monitor } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";
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
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuthSessions, useChangePassword, useRevokeSession } from "@/features/auth/api/auth";
import { AuthFormError, FieldError } from "@/features/auth/components/auth-form-error";
import { buildChangePasswordSchema } from "@/features/auth/schemas";
import { ApiError } from "@/lib/api/client";
import type { AuthSession } from "@/lib/api/types";

/**
 * The two things an account owner needs and nothing else: change the password, and see where
 * else this account is signed in.
 *
 * Both endpoints have existed since local auth landed; without a screen, a user who suspected
 * their password was known had no way to change it and no way to see the other device.
 */
export function SecuritySettings() {
  return (
    <div className="grid gap-6">
      <ChangePasswordCard />
      <SessionsCard />
    </div>
  );
}

function ChangePasswordCard() {
  const t = useTranslations("settings.security");
  const tValidation = useTranslations("auth.validation");
  const change = useChangePassword();

  const schema = useMemo(() => buildChangePasswordSchema(tValidation), [tValidation]);
  const serverErrors = change.error instanceof ApiError ? change.error.fieldErrors : undefined;

  const form = useForm({
    defaultValues: { currentPassword: "", newPassword: "" },
    validators: { onSubmit: schema },
    onSubmit: async ({ value, formApi }) => {
      await change.mutateAsync(value);
      // The API revokes every refresh token and hands this browser a fresh pair, so this device
      // stays signed in and the others cannot renew. Their *access* token lives out its last
      // few minutes — which is why the copy says other devices "will have to sign in again"
      // rather than claiming they are signed out this instant.
      //
      // Clearing the fields is what stops a shoulder-surfer reading the new password off a form
      // nobody closed.
      formApi.reset();
      toast.success(t("password.changed"));
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("password.title")}</CardTitle>
        <CardDescription>{t("password.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="grid max-w-md gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            event.stopPropagation();
            void form.handleSubmit();
          }}
        >
          {change.error ? <AuthFormError error={change.error} /> : null}

          <form.Field name="currentPassword">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("password.current")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  type="password"
                  autoComplete="current-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.currentPassword)
                  }
                />
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.currentPassword}
                />
              </div>
            )}
          </form.Field>

          <form.Field name="newPassword">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("password.new")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  type="password"
                  autoComplete="new-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.newPassword)
                  }
                />
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.newPassword}
                />
              </div>
            )}
          </form.Field>

          <p className="text-xs text-muted-foreground">{t("password.hint")}</p>

          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button type="submit" className="w-fit" disabled={!canSubmit || change.isPending}>
                {change.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <KeyRound className="size-4" />
                )}
                {t("password.submit")}
              </Button>
            )}
          </form.Subscribe>
        </form>
      </CardContent>
    </Card>
  );
}

function SessionsCard() {
  const t = useTranslations("settings.security");
  const sessions = useAuthSessions();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("sessions.title")}</CardTitle>
        <CardDescription>{t("sessions.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        {sessions.isPending ? (
          <Skeleton className="h-32" />
        ) : sessions.isError ? (
          <ErrorState error={sessions.error} onRetry={() => void sessions.refetch()} />
        ) : (
          <ul className="grid gap-2">
            {(sessions.data ?? []).map((session) => (
              <SessionRow key={session.id} session={session} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function SessionRow({ session }: { session: AuthSession }) {
  const t = useTranslations("settings.security");
  const format = useFormatter();
  const revoke = useRevokeSession();

  return (
    <li className="grid gap-2 rounded-lg border p-3 sm:grid-cols-[1fr_auto] sm:items-center">
      <div className="grid gap-1">
        <span className="flex flex-wrap items-center gap-2 text-sm">
          <Monitor className="size-4 text-muted-foreground" />
          <span className="truncate">{session.userAgent ?? t("sessions.unknownDevice")}</span>
          {/* Marked rather than hidden: a person scanning for something to revoke needs to know
              which row would sign *them* out. */}
          {session.current ? (
            <Badge variant="secondary">{t("sessions.thisDevice")}</Badge>
          ) : null}
        </span>
        <span className="flex flex-wrap gap-3 text-xs text-muted-foreground">
          {session.clientIp ? <span className="font-mono">{session.clientIp}</span> : null}
          <span>
            {t("sessions.lastUsed", {
              when: format.relativeTime(new Date(session.lastUsedAt ?? session.createdAt)),
            })}
          </span>
        </span>
      </div>

      {/* No button on the current row. Signing yourself out from a security screen is a footgun
          with a "sign out" already in the header. */}
      {session.current ? null : (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="ghost" size="sm" disabled={revoke.isPending}>
              {revoke.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <LogOut className="size-4" />
              )}
              {t("sessions.revoke")}
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{t("sessions.revokeTitle")}</AlertDialogTitle>
              <AlertDialogDescription>{t("sessions.revokeDescription")}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>{t("sessions.cancel")}</AlertDialogCancel>
              <AlertDialogAction
                onClick={() =>
                  revoke.mutate(session.id, {
                    onSuccess: () => toast.success(t("sessions.revoked")),
                    onError: (error) =>
                      toast.error(
                        error instanceof ApiError ? error.message : t("sessions.revokeFailed"),
                      ),
                  })
                }
              >
                {t("sessions.revoke")}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </li>
  );
}
