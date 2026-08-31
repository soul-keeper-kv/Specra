"use client";

import { useTranslations } from "next-intl";
import { useEffect } from "react";

import { ErrorState } from "@/components/common/error-state";

/**
 * Catches anything a page throws while rendering. Next resets the boundary by re-rendering the
 * segment, which is what `reset` does — so the retry button really re-runs the failed render
 * rather than reloading the whole document.
 */
export default function LocaleError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("errors");

  useEffect(() => {
    // The digest is the only handle on the server-side stack trace, which Next withholds in
    // production on purpose.
    console.error("Unhandled render error", error.digest, error);
  }, [error]);

  return (
    <main className="grid flex-1 place-items-center p-6">
      <div className="w-full max-w-md">
        <ErrorState error={error} onRetry={reset} />
        {error.digest ? (
          <p className="mt-2 text-center font-mono text-xs text-muted-foreground">
            {t("reference", { id: error.digest })}
          </p>
        ) : null}
      </div>
    </main>
  );
}
