"use client";

import { useTranslations } from "next-intl";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useUpdateProject } from "@/features/projects/api/projects";
import { ProjectForm } from "@/features/projects/components/project-form";
import { ApiError } from "@/lib/api/client";
import type { Project } from "@/lib/api/types";

/**
 * Renaming a project, or giving it a description it never got.
 *
 * Its own component because `useUpdateProject` takes the id at hook level — calling it from the
 * list would mean one hook per card, or a hook whose id changes as a dialog opens.
 *
 * The key is deliberately absent from the patch. It is immutable once created: generated files
 * and commit messages carry it, and letting somebody rename it here would leave a repository
 * full of references to a project key that no longer exists.
 */
export function ProjectEditDialog({
  project,
  onClose,
}: {
  project: Project;
  onClose: () => void;
}) {
  const t = useTranslations("projects");
  const update = useUpdateProject(project.id);

  return (
    <Dialog open onOpenChange={(open) => (open ? null : onClose())}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{t("edit.title")}</DialogTitle>
          <DialogDescription>{t("edit.description")}</DialogDescription>
        </DialogHeader>

        <ProjectForm
          defaultValues={{
            name: project.name,
            key: project.key,
            description: project.description ?? "",
          }}
          keyLocked
          submitLabel={t("edit.submit")}
          pending={update.isPending}
          serverErrors={update.error instanceof ApiError ? update.error.fieldErrors : undefined}
          onSubmit={(values) =>
            update.mutate(
              { name: values.name, description: values.description },
              {
                onSuccess: (saved) => {
                  onClose();
                  toast.success(t("edit.saved", { name: saved.name }));
                },
                onError: (error) =>
                  toast.error(error instanceof ApiError ? error.message : t("edit.failed")),
              },
            )
          }
        />
      </DialogContent>
    </Dialog>
  );
}
