"use client";

import { useForm } from "@tanstack/react-form";
import { ArrowDown, ArrowUp, Loader2, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { buildTestCaseSchema, type TestCaseFormValues } from "@/features/testcases/schemas";
import type { TestCasePriority } from "@/lib/api/types";

const PRIORITIES: TestCasePriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

type Props = {
  defaultValues: TestCaseFormValues;
  submitLabel: string;
  pending?: boolean;
  /** Field-level errors returned by the API's bean validation, keyed by field name. */
  serverErrors?: Record<string, string>;
  onSubmit: (values: TestCaseFormValues) => void | Promise<void>;
};

/**
 * The whole manual case in one form: what a QA person already writes in a spreadsheet, with the
 * steps as first-class rows they can add, reorder and remove. Submitting replaces the case —
 * the API's PUT semantics — so there is no per-step save to get half-applied.
 */
export function TestCaseForm({
  defaultValues,
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: Props) {
  const t = useTranslations("testcases.form");
  const tPriority = useTranslations("testcases.priority");
  const tValidation = useTranslations("testcases.validation");

  const schema = useMemo(() => buildTestCaseSchema(tValidation), [tValidation]);

  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => onSubmit(value),
  });

  return (
    <form
      className="grid gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        event.stopPropagation();
        void form.handleSubmit();
      }}
    >
      <div className="grid gap-5 sm:grid-cols-[1fr_12rem]">
        <form.Field name="title">
          {(field) => (
            <div className="grid gap-2">
              <Label htmlFor={field.name}>{t("title")}</Label>
              <Input
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder={t("titlePlaceholder")}
                aria-invalid={
                  field.state.meta.errors.length > 0 || Boolean(serverErrors?.title)
                }
              />
              <FieldError
                messages={field.state.meta.errors}
                serverMessage={serverErrors?.title}
              />
            </div>
          )}
        </form.Field>

        <form.Field name="priority">
          {(field) => (
            <div className="grid content-start gap-2">
              <Label htmlFor={field.name}>{t("priority")}</Label>
              <Select
                value={field.state.value}
                onValueChange={(value) => field.handleChange(value as TestCasePriority)}
              >
                <SelectTrigger id={field.name}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PRIORITIES.map((priority) => (
                    <SelectItem key={priority} value={priority}>
                      {tPriority(priority)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </form.Field>
      </div>

      <form.Field name="description">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("description")}</Label>
            <Textarea
              id={field.name}
              name={field.name}
              rows={3}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("descriptionPlaceholder")}
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.description}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="preconditions">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("preconditions")}</Label>
            <Textarea
              id={field.name}
              name={field.name}
              rows={2}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("preconditionsPlaceholder")}
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.preconditions}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="steps" mode="array">
        {(field) => (
          <div className="grid gap-3">
            <div className="flex items-center justify-between">
              <Label>{t("steps")}</Label>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => field.pushValue({ action: "", data: "", expected: "" })}
              >
                <Plus className="size-4" />
                {t("addStep")}
              </Button>
            </div>

            {field.state.value.length === 0 ? (
              <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
                {t("noSteps")}
              </p>
            ) : (
              <ol className="grid gap-2">
                {field.state.value.map((_, index) => (
                  <li
                    key={index}
                    className="grid gap-2 rounded-lg border bg-card p-3 sm:grid-cols-[2rem_1fr_1fr_1fr_auto] sm:items-start"
                  >
                    <span className="pt-2 text-sm font-medium text-muted-foreground tabular-nums">
                      {index + 1}.
                    </span>

                    <form.Field name={`steps[${index}].action`}>
                      {(subField) => (
                        <div className="grid gap-1">
                          <Textarea
                            rows={2}
                            value={subField.state.value}
                            onBlur={subField.handleBlur}
                            onChange={(event) => subField.handleChange(event.target.value)}
                            placeholder={t("stepActionPlaceholder")}
                            aria-label={t("stepAction", { position: index + 1 })}
                            aria-invalid={subField.state.meta.errors.length > 0}
                          />
                          <FieldError
                            messages={subField.state.meta.errors}
                            serverMessage={serverErrors?.[`steps[${index}].action`]}
                          />
                        </div>
                      )}
                    </form.Field>

                    <form.Field name={`steps[${index}].data`}>
                      {(subField) => (
                        <div className="grid gap-1">
                          <Textarea
                            rows={2}
                            value={subField.state.value}
                            onBlur={subField.handleBlur}
                            onChange={(event) => subField.handleChange(event.target.value)}
                            placeholder={t("stepDataPlaceholder")}
                            aria-label={t("stepData", { position: index + 1 })}
                            aria-invalid={subField.state.meta.errors.length > 0}
                          />
                          <FieldError
                            messages={subField.state.meta.errors}
                            serverMessage={serverErrors?.[`steps[${index}].data`]}
                          />
                        </div>
                      )}
                    </form.Field>

                    <form.Field name={`steps[${index}].expected`}>
                      {(subField) => (
                        <div className="grid gap-1">
                          <Textarea
                            rows={2}
                            value={subField.state.value}
                            onBlur={subField.handleBlur}
                            onChange={(event) => subField.handleChange(event.target.value)}
                            placeholder={t("stepExpectedPlaceholder")}
                            aria-label={t("stepExpected", { position: index + 1 })}
                            aria-invalid={subField.state.meta.errors.length > 0}
                          />
                          <FieldError messages={subField.state.meta.errors} />
                        </div>
                      )}
                    </form.Field>

                    <div className="flex gap-1 sm:flex-col">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="size-8"
                        disabled={index === 0}
                        aria-label={t("moveStepUp", { position: index + 1 })}
                        onClick={() => field.moveValue(index, index - 1)}
                      >
                        <ArrowUp className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="size-8"
                        disabled={index === field.state.value.length - 1}
                        aria-label={t("moveStepDown", { position: index + 1 })}
                        onClick={() => field.moveValue(index, index + 1)}
                      >
                        <ArrowDown className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="size-8 text-muted-foreground hover:text-destructive"
                        aria-label={t("removeStep", { position: index + 1 })}
                        onClick={() => field.removeValue(index)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </form.Field>

      <form.Field name="expectedResult">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("expectedResult")}</Label>
            <Textarea
              id={field.name}
              name={field.name}
              rows={2}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("expectedResultPlaceholder")}
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.expectedResult}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="tags">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("tags")}</Label>
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
              placeholder={t("tagsPlaceholder")}
            />
            <p className="text-xs text-muted-foreground">{t("tagsHint")}</p>
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
