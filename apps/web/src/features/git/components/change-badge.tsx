"use client";

import { useTranslations } from "next-intl";

import { cn } from "@/lib/utils";
import type { GitChangeKind } from "@/lib/api/types";

/**
 * git's own one-letter marks — A, M, D, U, C — because that is what an automation engineer
 * already reads in their editor, with the full word behind a title for everyone else.
 */
const LETTER: Record<GitChangeKind, string> = {
  ADDED: "A",
  MODIFIED: "M",
  DELETED: "D",
  UNTRACKED: "U",
  CONFLICTING: "C",
};

const TONE: Record<GitChangeKind, string> = {
  ADDED: "text-emerald-600 dark:text-emerald-400",
  MODIFIED: "text-amber-600 dark:text-amber-400",
  DELETED: "text-destructive",
  UNTRACKED: "text-muted-foreground",
  CONFLICTING: "text-destructive",
};

export function ChangeBadge({ kind }: { kind: GitChangeKind }) {
  const t = useTranslations("git.changeKind");
  return (
    <span
      className={cn("w-4 shrink-0 text-center font-mono text-xs font-semibold", TONE[kind])}
      title={t(kind)}
      aria-label={t(kind)}
    >
      {LETTER[kind]}
    </span>
  );
}
