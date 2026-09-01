"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useRegister } from "@/features/auth/api/auth";
import { AuthFormError, FieldError } from "@/features/auth/components/auth-form-error";
import { buildSignUpSchema, PASSWORD_MIN } from "@/features/auth/schemas";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";

/**
 * Creates an account and signs in with it, against `POST /api/v1/auth/register`.
 *
 * Registration also gives the new user a workspace of their own, so the dashboard has something to
 * show the moment they arrive — which is why this lands on `/dashboard` rather than on an empty
 * "create a workspace" screen.
 */
export function SignUpForm() {
  const t = useTranslations("auth.signUp");
  const tValidation = useTranslations("auth.validation");
  const router = useRouter();
  const register = useRegister();

  const schema = useMemo(() => buildSignUpSchema(tValidation), [tValidation]);
  const serverErrors =
    register.error instanceof ApiError ? register.error.fieldErrors : undefined;

  const form = useForm({
    defaultValues: { displayName: "", email: "", password: "" },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      await register.mutateAsync(value);
      router.replace("/dashboard");
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("subtitle")}</CardDescription>
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
          <AuthFormError error={register.error} />

          <form.Field name="displayName">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("name")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  autoComplete="name"
                  autoFocus
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  placeholder={t("namePlaceholder")}
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

          <form.Field name="email">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("email")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  type="email"
                  autoComplete="email"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  placeholder={t("emailPlaceholder")}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.email)
                  }
                />
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.email}
                />
              </div>
            )}
          </form.Field>

          <form.Field name="password">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("password")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  type="password"
                  autoComplete="new-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.password)
                  }
                />
                <p className="text-xs text-muted-foreground">
                  {t("passwordHint", { min: PASSWORD_MIN })}
                </p>
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.password}
                />
              </div>
            )}
          </form.Field>

          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button
                type="submit"
                className="w-full"
                disabled={!canSubmit || register.isPending}
              >
                {register.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                {t("submit")}
              </Button>
            )}
          </form.Subscribe>
        </form>
      </CardContent>
      <CardFooter className="justify-center text-sm text-muted-foreground">
        <span>
          {t("haveAccount")}{" "}
          <Link
            href="/sign-in"
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {t("signInLink")}
          </Link>
        </span>
      </CardFooter>
    </Card>
  );
}
