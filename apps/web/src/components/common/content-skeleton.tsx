import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export function ContentSkeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div className={cn("grid gap-3", className)} aria-hidden="true" {...props}>
      <Skeleton className="h-5 w-2/5" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
    </div>
  );
}
