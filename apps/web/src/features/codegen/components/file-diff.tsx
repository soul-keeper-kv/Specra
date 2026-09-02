"use client";

import { useTranslations } from "next-intl";
import { useMemo } from "react";

import type { GeneratedFile } from "@/lib/api/types";

/**
 * A line-level diff of one proposed file.
 *
 * Computed here rather than fetched: the API already sends both sides, and a diff view that
 * needed a round trip per file would make reviewing five files five times slower than reading
 * them. The algorithm is a plain LCS — the inputs are one generated file, not a monorepo.
 */
type DiffLine = {
  kind: "context" | "added" | "removed";
  text: string;
  before: number | null;
  after: number | null;
};

export function FileDiff({ file }: { file: GeneratedFile }) {
  const t = useTranslations("codegen.diff");
  const lines = useMemo(() => diff(file.previous ?? "", file.contents), [file]);

  if (file.status === "UNCHANGED") {
    return (
      <p className="px-4 py-6 text-center text-sm text-muted-foreground">{t("unchanged")}</p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse font-mono text-xs">
        <tbody>
          {lines.map((line, index) => (
            <tr
              key={index}
              className={
                line.kind === "added"
                  ? "bg-emerald-500/10"
                  : line.kind === "removed"
                    ? "bg-destructive/10"
                    : undefined
              }
            >
              <td className="w-10 shrink-0 border-r px-2 py-0.5 text-right text-muted-foreground select-none">
                {line.before ?? ""}
              </td>
              <td className="w-10 shrink-0 border-r px-2 py-0.5 text-right text-muted-foreground select-none">
                {line.after ?? ""}
              </td>
              <td className="w-6 px-1.5 py-0.5 text-center select-none">
                {line.kind === "added" ? "+" : line.kind === "removed" ? "-" : ""}
              </td>
              <td className="py-0.5 pr-4 whitespace-pre">{line.text || " "}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Longest common subsequence over lines, then a walk back through the table.
 *
 * A new file is the common case and short-circuits: every line is an addition, and building an
 * n×0 table to discover that would be work for nothing.
 */
function diff(before: string, after: string): DiffLine[] {
  const a = before === "" ? [] : before.split("\n");
  const b = after.split("\n");

  if (a.length === 0) {
    return b.map((text, index) => ({
      kind: "added" as const,
      text,
      before: null,
      after: index + 1,
    }));
  }

  const table: number[][] = Array.from({ length: a.length + 1 }, () =>
    new Array<number>(b.length + 1).fill(0),
  );
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      table[i]![j] =
        a[i] === b[j]
          ? table[i + 1]![j + 1]! + 1
          : Math.max(table[i + 1]![j]!, table[i]![j + 1]!);
    }
  }

  const lines: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      lines.push({ kind: "context", text: a[i]!, before: i + 1, after: j + 1 });
      i++;
      j++;
    } else if (table[i + 1]![j]! >= table[i]![j + 1]!) {
      lines.push({ kind: "removed", text: a[i]!, before: i + 1, after: null });
      i++;
    } else {
      lines.push({ kind: "added", text: b[j]!, before: null, after: j + 1 });
      j++;
    }
  }
  while (i < a.length) {
    lines.push({ kind: "removed", text: a[i]!, before: i + 1, after: null });
    i++;
  }
  while (j < b.length) {
    lines.push({ kind: "added", text: b[j]!, before: null, after: j + 1 });
    j++;
  }
  return lines;
}
