"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { noteSchema, type NoteFormValues } from "@/lib/schemas";

type Props = {
  defaultValues?: NoteFormValues;
  submitLabel: string;
  pending?: boolean;
  /** Field-level errors returned by the API's bean validation, keyed by field name. */
  serverErrors?: Record<string, string>;
  onSubmit: (values: NoteFormValues) => void | Promise<void>;
};

const EMPTY: NoteFormValues = { title: "", content: "", tags: [] };

export function NoteForm({
  defaultValues = EMPTY,
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: Props) {
  const form = useForm({
    defaultValues,
    // Zod 4 implements Standard Schema, so TanStack Form consumes it directly.
    validators: { onSubmit: noteSchema },
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
      <form.Field name="title">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>Title</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder="Kickoff notes"
              aria-invalid={field.state.meta.errors.length > 0 || Boolean(serverErrors?.title)}
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.title}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="content">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>Content</Label>
            <Textarea
              id={field.name}
              name={field.name}
              rows={12}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder="What happened, what was decided, what comes next…"
              aria-invalid={
                field.state.meta.errors.length > 0 || Boolean(serverErrors?.content)
              }
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.content}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="tags">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>Tags</Label>
            <Input
              id={field.name}
              name={field.name}
              defaultValue={field.state.value.join(", ")}
              onBlur={(event) =>
                field.handleChange(
                  event.target.value
                    .split(",")
                    .map((tag) => tag.trim())
                    .filter(Boolean),
                )
              }
              placeholder="meeting, q3"
            />
            <p className="text-xs text-muted-foreground">Comma separated. Stored lowercase.</p>
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
