"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";

import { Alert, AlertDescription } from "@/components/ui/alert";
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
import { useUpdateProfile } from "@/features/auth/api/auth";
import { AuthFormError, FieldError } from "@/features/auth/components/auth-form-error";
import { useSessionReady, useSessionUser } from "@/features/auth/store";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";

/**
 * The signed-in account, edited through `PATCH /api/v1/auth/me`.
 *
 * The email is shown and not editable: changing it is a different operation with a verification
 * step behind it, and an input that silently does nothing is worse than no input at all.
 */
export function ProfileSettings() {
  const t = useTranslations("settings.profile");
  const tNav = useTranslations("nav");
  const tValidation = useTranslations("auth.validation");
  const user = useSessionUser();
  const ready = useSessionReady();
  const update = useUpdateProfile();

  const serverErrors = update.error instanceof ApiError ? update.error.fieldErrors : undefined;

  const form = useForm({
    defaultValues: { displayName: user?.displayName ?? "" },
    onSubmit: async ({ value }) => {
      await update.mutateAsync({ displayName: value.displayName });
    },
  });

  if (ready && !user) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t("title")}</CardTitle>
        </CardHeader>
        <CardContent>
          <Alert>
            <AlertDescription>
              <Link href="/sign-in" className="underline underline-offset-4">
                {tNav("signIn")}
              </Link>
            </AlertDescription>
          </Alert>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            event.stopPropagation();
            void form.handleSubmit();
          }}
        >
          <AuthFormError error={update.error} />

          <form.Field
            name="displayName"
            validators={{
              onSubmit: ({ value }) =>
                value.trim().length === 0 ? tValidation("nameRequired") : undefined,
            }}
          >
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("name")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  disabled={!user}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.displayName)
                  }
                />
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.displayName}
                />
              </div>
            )}
          </form.Field>

          <div className="grid gap-2">
            <Label htmlFor="profile-email">{t("email")}</Label>
            <Input
              id="profile-email"
              type="email"
              value={user?.email ?? ""}
              readOnly
              disabled
            />
            <p className="text-xs text-muted-foreground">{t("emailHint")}</p>
          </div>

          <div className="flex items-center gap-3">
            <Button type="submit" disabled={!user || update.isPending}>
              {update.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {t("save")}
            </Button>
            {update.isSuccess ? (
              <span className="text-sm text-muted-foreground">{t("saved")}</span>
            ) : null}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
