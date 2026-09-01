"use client";

import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";

/**
 * A unified diff, coloured by line kind.
 *
 * Deliberately not a diff library: the API returns git's own output, and rendering it as text
 * with three colours is both honest about what it is and impossible to get subtly wrong. The
 * colours are tokens, so dark mode is not a second implementation.
 */
export function DiffView({ diff }: { diff: string }) {
  const t = useTranslations("git.diff");

  const lines = diff.replace(/\n$/, "").split("\n");

  if (diff.trim() === "") {
    return <p className="p-4 text-sm text-muted-foreground">{t("empty")}</p>;
  }

  return (
    <pre className="overflow-x-auto p-0 text-xs leading-relaxed">
      <code className="block font-mono">
        {lines.map((line, index) => (
          <span
            key={index}
            className={cn(
              "block px-4 whitespace-pre",
              line.startsWith("+") &&
                !line.startsWith("+++") &&
                "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
              line.startsWith("-") &&
                !line.startsWith("---") &&
                "bg-destructive/10 text-destructive",
              line.startsWith("@@") && "bg-muted text-muted-foreground",
              (line.startsWith("diff") || line.startsWith("index")) && "text-muted-foreground",
            )}
          >
            {line === "" ? " " : line}
          </span>
        ))}
      </code>
    </pre>
  );
}
