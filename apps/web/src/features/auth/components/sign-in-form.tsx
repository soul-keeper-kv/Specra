"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
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
import { useLogin } from "@/features/auth/api/auth";
import {
  AuthFormError,
  FieldError,
  SessionEndedNotice,
} from "@/features/auth/components/auth-form-error";
import { buildSignInSchema } from "@/features/auth/schemas";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";

/**
 * Email and password, against `POST /api/v1/auth/login`.
 *
 * Where the user lands afterwards is whatever sent them here: the route guard puts the page it
 * refused on `?next=`, so a deep link that expired mid-session resumes instead of dumping the user
 * on the dashboard. Anything that is not a path inside this app is ignored — an open redirect is a
 * phishing primitive, and a login page is exactly where it pays off.
 *
 * A `?reason=` alongside it says the session ended on its own rather than the user navigating
 * here, which is the difference between a blank form and one that explains itself.
 */
export function SignInForm() {
  const t = useTranslations("auth.signIn");
  const tValidation = useTranslations("auth.validation");
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useLogin();

  const schema = useMemo(() => buildSignInSchema(tValidation), [tValidation]);
  const serverErrors = login.error instanceof ApiError ? login.error.fieldErrors : undefined;

  const form = useForm({
    defaultValues: { email: "", password: "" },
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      await login.mutateAsync(value);
      router.replace(safeNext(searchParams.get("next")));
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
          <SessionEndedNotice reason={searchParams.get("reason")} />
          <AuthFormError error={login.error} />

          <form.Field name="email">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("email")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  type="email"
                  autoComplete="email"
                  autoFocus
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
                  autoComplete="current-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={
                    field.state.meta.errors.length > 0 || Boolean(serverErrors?.password)
                  }
                />
                <FieldError
                  messages={field.state.meta.errors}
                  serverMessage={serverErrors?.password}
                />
              </div>
            )}
          </form.Field>

          <form.Subscribe selector={(state) => state.canSubmit}>
            {(canSubmit) => (
              <Button type="submit" className="w-full" disabled={!canSubmit || login.isPending}>
                {login.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                {t("submit")}
              </Button>
            )}
          </form.Subscribe>
        </form>
      </CardContent>
      <CardFooter className="justify-center text-sm text-muted-foreground">
        <span>
          {t("noAccount")}{" "}
          <Link
            href="/sign-up"
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {t("signUpLink")}
          </Link>
        </span>
      </CardFooter>
    </Card>
  );
}

/**
 * Only a path inside this app, and never one that starts a new sign-in loop.
 *
 * `//evil.example` is a protocol-relative URL that a naive check for a leading slash lets through,
 * which is why the second character is tested too.
 */
function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return "/dashboard";
  if (next.startsWith("/sign-in") || next.startsWith("/sign-up")) return "/dashboard";
  return next;
}
