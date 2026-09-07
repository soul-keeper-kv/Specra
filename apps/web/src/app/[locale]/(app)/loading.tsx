import { ContentSkeleton } from "@/components/common/content-skeleton";

export default function WorkspaceLoading() {
  return (
    <div className="grid gap-6" aria-busy="true">
      <ContentSkeleton className="max-w-2xl" />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <ContentSkeleton key={index} className="rounded-lg border bg-card p-6" />
        ))}
      </div>

      <ContentSkeleton className="min-h-64 rounded-lg border bg-card p-6" />
    </div>
  );
}
