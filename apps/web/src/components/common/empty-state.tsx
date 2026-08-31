import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Shown where a list has nothing in it. An empty table with no explanation reads as a bug, so this
 * always takes a next step rather than only a message.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon?: LucideIcon;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="grid place-items-center gap-3 rounded-lg border border-dashed px-6 py-14 text-center">
      {Icon ? <Icon className="size-6 text-muted-foreground" /> : null}
      <div className="grid gap-1">
        <p className="font-medium">{title}</p>
        {description ? (
          <p className="max-w-sm text-sm text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {action}
    </div>
  );
}
