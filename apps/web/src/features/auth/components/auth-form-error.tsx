"use client";

import { AlertTriangle } from "lucide-react";

import { ApiError } from "@/lib/api/client";

/**
 * The banner above a sign-in or sign-up form.
 *
 * It shows the API's own sentence rather than a message chosen from the `code`. The API translates
 * `detail` per request and it is written to be actionable — "try again in about 12 minutes", "there
 * is already an account for x@y" — and re-deriving that here would mean two copies of every
 * wording, one of which would go stale.
 *
 * Field-level failures are excluded: those are already rendered under their own inputs by
 * {@link FieldError}, and repeating them at the top reads as two separate problems.
 */
export function AuthFormError({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return null;
  if (error.code === "validation-failed" && Object.keys(error.fieldErrors).length > 0)
    return null;

  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive"
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <div className="grid gap-1">
        <span>{error.message}</span>
        {error.requestId ? (
          <span className="font-mono text-xs opacity-70">{error.requestId}</span>
        ) : null}
      </div>
    </div>
  );
}

/** A server-side message wins: it is the validation that actually rejected the request. */
export function FieldError({
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
