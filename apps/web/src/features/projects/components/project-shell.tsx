"use client";

import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

import { ErrorState } from "@/components/common/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useProject } from "@/features/projects/api/projects";
import { ProjectTabs } from "@/features/projects/components/project-tabs";
import { Link } from "@/i18n/navigation";

/**
 * The frame every page inside one project shares: the way back, the project's name and key, and
 * whatever the page itself renders below. Fetching the project here means a child page can assume
 * it exists — a 404 never gets as far as the content.
 */
export function ProjectShell({
  projectId,
  children,
}: {
  projectId: string;
  children: ReactNode;
}) {
  const t = useTranslations("projects.detail");

  const project = useProject(projectId);

  if (project.isPending) {
    return (
      <div className="grid gap-6">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (project.isError) {
    return (
      <div className="grid gap-4">
        <Button asChild variant="ghost" size="sm" className="w-fit">
          <Link href="/projects">
            <ArrowLeft className="size-4" />
            {t("back")}
          </Link>
        </Button>
        <ErrorState error={project.error} onRetry={() => void project.refetch()} />
      </div>
    );
  }

  return (
    <div className="grid gap-6">
      <div className="grid gap-2">
        <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
          <Link href="/projects">
            <ArrowLeft className="size-4" />
            {t("back")}
          </Link>
        </Button>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-heading text-2xl font-semibold tracking-tight">
            {project.data.name}
          </h1>
          <Badge variant="outline" className="font-mono font-normal">
            {project.data.key}
          </Badge>
        </div>
        {project.data.description ? (
          <p className="max-w-2xl text-sm text-muted-foreground">{project.data.description}</p>
        ) : null}
      </div>

      <ProjectTabs projectId={projectId} />

      {children}
    </div>
  );
}
