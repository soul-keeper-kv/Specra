"use client";

import { useForm } from "@tanstack/react-form";
import { useTranslations } from "next-intl";
import { z } from "zod";

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
import { useAuthStore } from "@/features/auth/store";
import { useRouter } from "@/i18n/navigation";

/**
 * Stand-in sign-in. There is no auth endpoint yet, so this writes the local session and moves on.
 *
 * The schema is built inside the component because its messages are translated: a module-level
 * `z.object` would capture whichever locale happened to load first.
 */
export function SignInForm() {
  const t = useTranslations("auth.signIn");
  const tValidation = useTranslations("auth.validation");
  const signIn = useAuthStore((state) => state.signIn);
  const router = useRouter();

  const schema = z.object({
    name: z.string().trim().min(1, tValidation("nameRequired")),
    email: z.string().trim().email(tValidation("emailInvalid")),
  });

  const form = useForm({
    defaultValues: { name: "", email: "" },
    validators: { onSubmit: schema },
    onSubmit: ({ value }) => {
      signIn(value);
      router.push("/dashboard");
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
          <form.Field name="name">
            {(field) => (
              <div className="grid gap-2">
                <Label htmlFor={field.name}>{t("name")}</Label>
                <Input
                  id={field.name}
                  name={field.name}
                  autoComplete="name"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(event) => field.handleChange(event.target.value)}
                  placeholder={t("namePlaceholder")}
                  aria-invalid={field.state.meta.errors.length > 0}
                />
                <FieldError messages={field.state.meta.errors} />
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
                  aria-invalid={field.state.meta.errors.length > 0}
                />
                <FieldError messages={field.state.meta.errors} />
              </div>
            )}
          </form.Field>

          <Button type="submit" className="w-full">
            {t("submit")}
          </Button>

          <p className="text-xs text-muted-foreground">{t("notice")}</p>
        </form>
      </CardContent>
    </Card>
  );
}

function FieldError({ messages }: { messages: unknown[] }) {
  const text = messages
    .map((error) =>
      typeof error === "string" ? error : (error as { message?: string })?.message,
    )
    .filter(Boolean)
    .join(", ");

  if (!text) return null;
  return <p className="text-sm text-destructive">{text}</p>;
}
