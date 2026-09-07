"use client";

import { History } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { useTestModelVersions } from "@/features/testmodel/api/test-models";

/**
 * Every stored version of one test case's Test Model.
 *
 * The versions exist because a human edit is kept as version n+1 rather than overwriting what it
 * corrected — regeneration and impact analysis both diff against what came before. That made the
 * history load-bearing without ever making it visible; this is the smallest thing that answers
 * "how many times has this been changed, and by what".
 *
 * Fetched only when opened. A list nobody looks at is not worth a request on every render of the
 * workspace.
 */
export function ModelHistory({
  testCaseId,
  currentVersion,
}: {
  testCaseId: string | undefined;
  currentVersion: number;
}) {
  const t = useTranslations("testManagement.workspace.history");
  const format = useFormatter();
  const versions = useTestModelVersions(testCaseId);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="sm" className="h-7 gap-1.5 px-2">
          <History className="size-3.5" />
          {t("action")}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80">
        <p className="mb-2 text-sm font-medium">{t("title")}</p>

        {versions.isPending || versions.isError ? (
          <div className="grid gap-2" aria-hidden="true">
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-6 w-4/5" />
            <Skeleton className="h-6 w-3/5" />
          </div>
        ) : (
          <ul className="grid gap-1">
            {(versions.data ?? []).map((version) => (
              <li
                key={version.id}
                className="flex flex-wrap items-baseline gap-2 rounded px-1.5 py-1 text-xs"
              >
                <span
                  className={
                    version.version === currentVersion ? "font-mono font-semibold" : "font-mono"
                  }
                >
                  v{version.version}
                </span>
                {version.version === currentVersion ? (
                  <span className="text-muted-foreground">{t("current")}</span>
                ) : null}
                <span className="text-muted-foreground">
                  {t("steps", { count: version.stepCount })}
                </span>
                <span className="ms-auto text-muted-foreground">
                  {format.relativeTime(new Date(version.createdAt))}
                </span>
              </li>
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  );
}
