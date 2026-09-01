"use client";

import {
  Building2,
  FolderKanban,
  Loader2,
  MoreVertical,
  Plus,
  Search,
  Trash2,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import { toast } from "sonner";

import { EmptyState } from "@/components/common/empty-state";
import { ErrorState } from "@/components/common/error-state";
import { PageHeader } from "@/components/common/page-header";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCreateProject,
  useDeleteProject,
  useProjects,
} from "@/features/projects/api/projects";
import { ProjectForm } from "@/features/projects/components/project-form";
import { useActiveWorkspace, useCreateWorkspace } from "@/features/workspaces/api/workspaces";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";
import type { Project } from "@/lib/api/types";

const PAGE_SIZE = 12;

export function ProjectsView() {
  const t = useTranslations("projects");
  const tErrors = useTranslations("errors");

  const active = useActiveWorkspace();

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  // The list view carries its own header (its actions need the workspace id), so only the
  // not-ready branches render this bare one — the page always has its h1, API up or down.
  if (active.isPending) {
    return (
      <div className="grid gap-6">
        <PageHeader title={t("title")} description={t("subtitle")} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-32 w-full" />
          ))}
        </div>
      </div>
    );
  }

  if (active.isError) {
    return (
      <div className="grid gap-6">
        <PageHeader title={t("title")} description={t("subtitle")} />
        <ErrorState error={active.error} onRetry={() => void active.refetch()} />
      </div>
    );
  }

  if (!active.workspace) {
    return (
      <div className="grid gap-6">
        <PageHeader title={t("title")} description={t("subtitle")} />
        <CreateWorkspaceGate describe={describe} />
      </div>
    );
  }

  return <ProjectList workspaceId={active.workspace.id} describe={describe} />;
}

/**
 * Everything lives in a workspace, so the very first visit has one thing to do. Explicit rather
 * than auto-created: the name ends up in every URL slug and every future invitation.
 */
function CreateWorkspaceGate({ describe }: { describe: (error: unknown) => string }) {
  const t = useTranslations("workspaces");
  const createWorkspace = useCreateWorkspace();
  const [name, setName] = useState("");

  const trimmed = name.trim();

  function submit() {
    if (!trimmed) return;
    createWorkspace.mutate(
      { name: trimmed },
      {
        onSuccess: (workspace) => toast.success(t("toast.created", { name: workspace.name })),
        onError: (error) => toast.error(describe(error)),
      },
    );
  }

  return (
    <EmptyState
      icon={Building2}
      title={t("empty.title")}
      description={t("empty.description")}
      action={
        <form
          className="flex w-full max-w-sm items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <div className="grid flex-1 gap-2 text-left">
            <Label htmlFor="workspace-name">{t("create.name")}</Label>
            <Input
              id="workspace-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder={t("create.namePlaceholder")}
              maxLength={120}
            />
          </div>
          <Button type="submit" disabled={!trimmed || createWorkspace.isPending}>
            {createWorkspace.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            {t("create.submit")}
          </Button>
        </form>
      }
    />
  );
}

function ProjectList({
  workspaceId,
  describe,
}: {
  workspaceId: string;
  describe: (error: unknown) => string;
}) {
  const t = useTranslations("projects");
  const tActions = useTranslations("actions");
  const format = useFormatter();

  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Project | null>(null);

  const debouncedSearch = useDebouncedValue(search);

  const projects = useProjects(workspaceId, {
    q: debouncedSearch || undefined,
    page,
    size: PAGE_SIZE,
  });
  const createProject = useCreateProject(workspaceId);
  const deleteProject = useDeleteProject();

  function confirmDelete(project: Project) {
    deleteProject.mutate(project.id, {
      onSuccess: () => toast.success(t("toast.deleted", { name: project.name })),
      onError: (error) => toast.error(describe(error)),
      onSettled: () => setPendingDelete(null),
    });
  }

  const isEmpty = projects.data?.content.length === 0;

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("title")}
        description={t("subtitle")}
        actions={
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="size-4" />
                {t("new.trigger")}
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-xl">
              <DialogHeader>
                <DialogTitle>{t("new.title")}</DialogTitle>
                <DialogDescription>{t("new.description")}</DialogDescription>
              </DialogHeader>
              <ProjectForm
                submitLabel={t("new.submit")}
                pending={createProject.isPending}
                serverErrors={
                  createProject.error instanceof ApiError
                    ? createProject.error.fieldErrors
                    : undefined
                }
                onSubmit={(values) =>
                  createProject.mutate(
                    {
                      name: values.name,
                      key: values.key ? values.key.toUpperCase() : undefined,
                      description: values.description || undefined,
                    },
                    {
                      onSuccess: (project) => {
                        toast.success(t("toast.created", { name: project.name }));
                        setCreateOpen(false);
                      },
                      onError: (error) => toast.error(describe(error)),
                    },
                  )
                }
              />
            </DialogContent>
          </Dialog>
        }
      />

      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            // A narrower result set may not have this page; start over rather than land empty.
            setPage(0);
          }}
          placeholder={t("searchPlaceholder")}
          aria-label={tActions("search")}
          className="pl-9"
        />
      </div>

      {projects.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={index} className="h-32 w-full" />
          ))}
        </div>
      ) : projects.isError ? (
        <ErrorState error={projects.error} onRetry={() => void projects.refetch()} />
      ) : isEmpty ? (
        <EmptyState
          icon={FolderKanban}
          title={t("empty.title")}
          description={t("empty.hint")}
          action={
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="size-4" />
              {t("new.trigger")}
            </Button>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {projects.data.content.map((project) => (
              <Card
                key={project.id}
                className="relative transition-colors hover:border-primary/40"
              >
                <CardHeader>
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base leading-snug">
                      <Link
                        href={`/projects/${project.id}`}
                        className="after:absolute after:inset-0 hover:underline"
                      >
                        {project.name}
                      </Link>
                    </CardTitle>
                    {/* Above the card's stretched link, so the menu stays clickable. */}
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="relative z-10 -mt-1 -mr-2 size-8"
                          aria-label={tActions("edit")}
                        >
                          <MoreVertical className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem
                          variant="destructive"
                          onSelect={() => setPendingDelete(project)}
                        >
                          <Trash2 className="size-4" />
                          {tActions("delete")}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                  <CardDescription className="line-clamp-2 min-h-10">
                    {project.description ?? t("card.noDescription")}
                  </CardDescription>
                  <div className="flex items-center gap-2 pt-1 text-xs text-muted-foreground">
                    <Badge variant="outline" className="font-mono font-normal">
                      {project.key}
                    </Badge>
                    <span className="tabular-nums">
                      {format.dateTime(new Date(project.updatedAt), { dateStyle: "medium" })}
                    </span>
                  </div>
                </CardHeader>
              </Card>
            ))}
          </div>

          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {t("count", { count: projects.data.totalElements })}
              {projects.isFetching ? (
                <Loader2 className="ml-2 inline size-3 animate-spin" />
              ) : null}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={projects.data.first}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                {tActions("previous")}
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={projects.data.last}
                onClick={() => setPage((current) => current + 1)}
              >
                {tActions("next")}
              </Button>
            </div>
          </div>
        </>
      )}

      {/* Deleting a project takes its test cases with it; that is worth one confirmation. */}
      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => (open ? null : setPendingDelete(null))}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDelete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDelete.description", { name: pendingDelete?.name ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={deleteProject.isPending}
              onClick={() => pendingDelete && confirmDelete(pendingDelete)}
            >
              {t("confirmDelete.confirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
