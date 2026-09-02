"use client";

import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import type { RunItemStatus, RunStatus } from "@/lib/api/types";

/**
 * FAILED and ERROR look different on purpose.
 *
 * A failure says something about the application under test and is what a person came here to
 * read; an error says the run could not complete and is usually ours to fix. Painting both red
 * would ask the user to read the message to find out which of the two happened.
 */
const RUN_VARIANT: Record<RunStatus, "outline" | "secondary" | "default" | "destructive"> = {
  QUEUED: "outline",
  RUNNING: "secondary",
  PASSED: "default",
  FAILED: "destructive",
  ERROR: "outline",
  CANCELLED: "outline",
};

const ITEM_VARIANT: Record<RunItemStatus, "outline" | "secondary" | "default" | "destructive"> =
  {
    QUEUED: "outline",
    RUNNING: "secondary",
    PASSED: "default",
    FAILED: "destructive",
    ERROR: "outline",
    SKIPPED: "outline",
  };

export function RunStatusBadge({ status }: { status: RunStatus }) {
  const t = useTranslations("runs.status");
  return (
    <Badge
      variant={RUN_VARIANT[status]}
      className={
        status === "ERROR"
          ? "border-destructive/50 font-normal text-destructive"
          : "font-normal"
      }
    >
      {t(status)}
    </Badge>
  );
}

export function RunItemStatusBadge({ status }: { status: RunItemStatus }) {
  const t = useTranslations("runs.itemStatus");
  return (
    <Badge
      variant={ITEM_VARIANT[status]}
      className={
        status === "ERROR"
          ? "border-destructive/50 font-normal text-destructive"
          : "font-normal"
      }
    >
      {t(status)}
    </Badge>
  );
}
