"use client";

import { useForm } from "@tanstack/react-form";
import { KeyRound, Loader2, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  buildEnvironmentSchema,
  type EnvironmentFormValues,
  type EnvironmentVariableValues,
} from "@/features/environments/schemas";
import type { Environment } from "@/lib/api/types";

type Props = {
  environment?: Environment;
  submitLabel: string;
  pending?: boolean;
  /** Field-level errors from the API's bean validation, keyed by field name. */
  serverErrors?: Record<string, string>;
  onSubmit: (values: EnvironmentFormValues) => void | Promise<void>;
};

const EMPTY: EnvironmentFormValues = {
  name: "",
  baseUrl: "",
  isDefault: false,
  variables: [],
};

const NEW_VARIABLE: EnvironmentVariableValues = {
  key: "",
  value: "",
  secret: false,
  stored: false,
};

/**
 * Turns a response into form values.
 *
 * A stored secret arrives with `value: null` and `valueSet: true`, because the API never returns
 * one. It becomes an empty field marked `stored`, which is what lets the form be re-saved — an
 * omitted secret means "keep what is stored", so renaming an environment does not wipe every
 * credential.
 */
function toFormValues(environment: Environment | undefined): EnvironmentFormValues {
  if (!environment) return EMPTY;
  return {
    name: environment.name,
    baseUrl: environment.baseUrl,
    isDefault: environment.isDefault,
    variables: environment.variables.map((variable) => ({
      key: variable.key,
      value: variable.value ?? "",
      secret: variable.secret,
      stored: variable.secret && variable.valueSet,
    })),
  };
}

export function EnvironmentForm({
  environment,
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: Props) {
  const t = useTranslations("environments.form");
  const tValidation = useTranslations("environments.validation");

  const schema = useMemo(() => buildEnvironmentSchema(tValidation), [tValidation]);
  const defaultValues = useMemo(() => toFormValues(environment), [environment]);

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
              className="font-mono"
              aria-invalid={field.state.meta.errors.length > 0 || Boolean(serverErrors?.name)}
            />
            <FieldError messages={field.state.meta.errors} serverMessage={serverErrors?.name} />
          </div>
        )}
      </form.Field>

      <form.Field name="baseUrl">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("baseUrl")}</Label>
            <Input
              id={field.name}
              name={field.name}
              inputMode="url"
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("baseUrlPlaceholder")}
              aria-invalid={
                field.state.meta.errors.length > 0 || Boolean(serverErrors?.baseUrl)
              }
            />
            <p className="text-xs text-muted-foreground">{t("baseUrlHint")}</p>
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.baseUrl}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="isDefault">
        {(field) => (
          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div className="grid gap-1">
              <Label htmlFor={field.name}>{t("isDefault")}</Label>
              <p className="text-xs text-muted-foreground">{t("isDefaultHint")}</p>
            </div>
            <Switch
              id={field.name}
              checked={field.state.value}
              onCheckedChange={(checked) => field.handleChange(checked)}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="variables" mode="array">
        {(field) => (
          <div className="grid gap-3">
            <div className="grid gap-1">
              <Label>{t("variables")}</Label>
              <p className="text-xs text-muted-foreground">{t("variablesHint")}</p>
            </div>

            {field.state.value.length === 0 ? (
              <p className="rounded-lg border border-dashed p-4 text-center text-sm text-muted-foreground">
                {t("noVariables")}
              </p>
            ) : (
              <ul className="grid gap-3">
                {field.state.value.map((_, index) => (
                  <li key={index} className="grid gap-2 rounded-lg border p-3">
                    <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto] sm:items-start">
                      <form.Field name={`variables[${index}].key`}>
                        {(keyField) => (
                          <div className="grid gap-1">
                            <Label htmlFor={keyField.name} className="sr-only">
                              {t("variableKey")}
                            </Label>
                            <Input
                              id={keyField.name}
                              name={keyField.name}
                              value={keyField.state.value}
                              onBlur={keyField.handleBlur}
                              onChange={(event) => keyField.handleChange(event.target.value)}
                              placeholder={t("variableKeyPlaceholder")}
                              className="font-mono"
                              aria-invalid={keyField.state.meta.errors.length > 0}
                            />
                            <FieldError messages={keyField.state.meta.errors} />
                          </div>
                        )}
                      </form.Field>

                      <form.Subscribe
                        selector={(state) => ({
                          secret: state.values.variables[index]?.secret ?? false,
                          stored: state.values.variables[index]?.stored ?? false,
                        })}
                      >
                        {({ secret, stored }) => (
                          <form.Field name={`variables[${index}].value`}>
                            {(valueField) => (
                              <div className="grid gap-1">
                                <Label htmlFor={valueField.name} className="sr-only">
                                  {t("variableValue")}
                                </Label>
                                <Input
                                  id={valueField.name}
                                  name={valueField.name}
                                  type={secret ? "password" : "text"}
                                  autoComplete="off"
                                  value={valueField.state.value}
                                  onBlur={valueField.handleBlur}
                                  onChange={(event) =>
                                    valueField.handleChange(event.target.value)
                                  }
                                  placeholder={
                                    stored
                                      ? t("secretStoredPlaceholder")
                                      : t("variableValuePlaceholder")
                                  }
                                  aria-invalid={valueField.state.meta.errors.length > 0}
                                />
                                {stored ? (
                                  <p className="flex items-center gap-1 text-xs text-muted-foreground">
                                    <KeyRound className="size-3" />
                                    {t("secretStored")}
                                  </p>
                                ) : null}
                                <FieldError messages={valueField.state.meta.errors} />
                              </div>
                            )}
                          </form.Field>
                        )}
                      </form.Subscribe>

                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => field.removeValue(index)}
                        aria-label={t("removeVariable")}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>

                    <form.Field name={`variables[${index}].secret`}>
                      {(secretField) => (
                        <div className="flex items-center gap-2">
                          <Switch
                            id={`${secretField.name}-toggle`}
                            checked={secretField.state.value}
                            onCheckedChange={(checked) => {
                              secretField.handleChange(checked);
                              // Turning a secret back into a plain variable leaves no ciphertext
                              // to carry forward, so the row stops claiming one is stored.
                              if (!checked) {
                                form.setFieldValue(`variables[${index}].stored`, false);
                              }
                            }}
                          />
                          <Label
                            htmlFor={`${secretField.name}-toggle`}
                            className="text-xs font-normal"
                          >
                            {t("variableSecret")}
                          </Label>
                        </div>
                      )}
                    </form.Field>
                  </li>
                ))}
              </ul>
            )}

            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-fit"
              onClick={() => field.pushValue({ ...NEW_VARIABLE })}
            >
              <Plus className="size-4" />
              {t("addVariable")}
            </Button>
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
