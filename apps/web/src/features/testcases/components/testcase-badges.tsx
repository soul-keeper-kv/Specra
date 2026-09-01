"use client";

import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import type { AutomationStatus, TestCasePriority } from "@/lib/api/types";

const STATUS_VARIANT: Record<AutomationStatus, "outline" | "secondary" | "default"> = {
  NOT_AUTOMATED: "outline",
  MODELLED: "secondary",
  GENERATED: "secondary",
  COMMITTED: "default",
};

const PRIORITY_VARIANT: Record<
  TestCasePriority,
  "outline" | "secondary" | "default" | "destructive"
> = {
  LOW: "outline",
  MEDIUM: "secondary",
  HIGH: "default",
  CRITICAL: "destructive",
};

export function StatusBadge({ status }: { status: AutomationStatus }) {
  const t = useTranslations("testcases.status");
  return (
    <Badge variant={STATUS_VARIANT[status]} className="font-normal">
      {t(status)}
    </Badge>
  );
}

/** The "regenerate me" flag: the text moved on after the IR was made. */
export function OutOfDateBadge() {
  const t = useTranslations("testcases.status");
  return (
    <Badge variant="outline" className="border-destructive/50 font-normal text-destructive">
      {t("outOfDate")}
    </Badge>
  );
}

export function PriorityBadge({ priority }: { priority: TestCasePriority }) {
  const t = useTranslations("testcases.priority");
  return (
    <Badge variant={PRIORITY_VARIANT[priority]} className="font-normal">
      {t(priority)}
    </Badge>
  );
}
