"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { buildProjectSchema, type ProjectFormValues } from "@/features/projects/schemas";

type Props = {
  defaultValues?: ProjectFormValues;
  submitLabel: string;
  pending?: boolean;
  /** Field-level errors returned by the API's bean validation, keyed by field name. */
  serverErrors?: Record<string, string>;
  onSubmit: (values: ProjectFormValues) => void | Promise<void>;
};

const EMPTY: ProjectFormValues = { name: "", key: "", description: "" };

export function ProjectForm({
  defaultValues = EMPTY,
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: Props) {
  const t = useTranslations("projects.form");
  const tValidation = useTranslations("projects.validation");

  const schema = useMemo(() => buildProjectSchema(tValidation), [tValidation]);

  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => onSubmit(value),
  });

  return (
    <form
      className="grid gap-5"
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

      <form.Field name="key">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("key")}</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value.toUpperCase())}
              placeholder={t("keyPlaceholder")}
              className="max-w-40 font-mono uppercase"
              aria-invalid={field.state.meta.errors.length > 0 || Boolean(serverErrors?.key)}
            />
            <p className="text-xs text-muted-foreground">{t("keyHint")}</p>
            <FieldError messages={field.state.meta.errors} serverMessage={serverErrors?.key} />
          </div>
        )}
      </form.Field>

      <form.Field name="description">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("description")}</Label>
            <Textarea
              id={field.name}
              name={field.name}
              rows={4}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("descriptionPlaceholder")}
              aria-invalid={
                field.state.meta.errors.length > 0 || Boolean(serverErrors?.description)
              }
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.description}
            />
          </div>
        )}
      </form.Field>

      <form.Subscribe selector={(state) => state.canSubmit}>
        {(canSubmit) => (
          <div className="flex gap-2">
            <Button type="submit" disabled={!canSubmit || pending}>
              {pending ? <Loader2 className="size-4 animate-spin" /> : null}
              {submitLabel}
            </Button>
          </div>
        )}
      </form.Subscribe>
    </form>
  );
}

/** A server-side message wins: it is the validation that actually rejected the write. */
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
