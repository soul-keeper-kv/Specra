"use client";

import { useForm } from "@tanstack/react-form";
import { Loader2 } from "lucide-react";
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
import { buildConnectSchema, type ConnectFormValues } from "@/features/git/schemas";
import type { GitCredential, GitProviderKind } from "@/lib/api/types";

/** Radix's Select cannot carry an empty value, so "no credential" needs a token of its own. */
const NO_CREDENTIAL = "none";

const PROVIDERS: GitProviderKind[] = ["GITHUB", "GITLAB", "BITBUCKET"];

type Props = {
  defaultValues?: ConnectFormValues;
  credentials: GitCredential[];
  submitLabel: string;
  pending?: boolean;
  serverErrors?: Record<string, string>;
  onSubmit: (values: ConnectFormValues) => void;
};

const EMPTY: ConnectFormValues = {
  provider: "GITHUB",
  remoteUrl: "",
  defaultBranch: "main",
  credentialId: NO_CREDENTIAL,
};

export function ConnectRepositoryForm({
  defaultValues = EMPTY,
  credentials,
  submitLabel,
  pending,
  serverErrors,
  onSubmit,
}: Props) {
  const t = useTranslations("git.connect");
  const tValidation = useTranslations("git.validation");

  const schema = useMemo(() => buildConnectSchema(tValidation), [tValidation]);

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
      <form.Field name="provider">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("provider")}</Label>
            <Select
              value={field.state.value}
              onValueChange={(value) => field.handleChange(value as GitProviderKind)}
            >
              <SelectTrigger id={field.name} className="w-56">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PROVIDERS.map((provider) => (
                  <SelectItem key={provider} value={provider}>
                    {provider}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {/* Only GitHub has an implementation; the others are stored and refused with a
                problem document rather than hidden, so the roadmap is visible. */}
            <p className="text-xs text-muted-foreground">{t("providerHint")}</p>
          </div>
        )}
      </form.Field>

      <form.Field name="remoteUrl">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("remoteUrl")}</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder={t("remoteUrlPlaceholder")}
              aria-invalid={
                field.state.meta.errors.length > 0 || Boolean(serverErrors?.remoteUrl)
              }
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.remoteUrl}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="defaultBranch">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("defaultBranch")}</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder="main"
              className="max-w-56"
              aria-invalid={
                field.state.meta.errors.length > 0 || Boolean(serverErrors?.defaultBranch)
              }
            />
            <FieldError
              messages={field.state.meta.errors}
              serverMessage={serverErrors?.defaultBranch}
            />
          </div>
        )}
      </form.Field>

      <form.Field name="credentialId">
        {(field) => (
          <div className="grid gap-2">
            <Label htmlFor={field.name}>{t("credential")}</Label>
            <Select value={field.state.value} onValueChange={field.handleChange}>
              <SelectTrigger id={field.name} className="w-72">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_CREDENTIAL}>{t("noCredential")}</SelectItem>
                {credentials.map((credential) => (
                  <SelectItem key={credential.id} value={credential.id}>
                    {credential.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">{t("credentialHint")}</p>
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

/** Turns the form's "none" token back into an omitted field for the API. */
export function toRepositoryInput(values: ConnectFormValues) {
  return {
    provider: values.provider,
    remoteUrl: values.remoteUrl,
    defaultBranch: values.defaultBranch,
    credentialId: values.credentialId === NO_CREDENTIAL ? undefined : values.credentialId,
  };
}

export { NO_CREDENTIAL };

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
