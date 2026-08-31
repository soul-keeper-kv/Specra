"use client";

import { ArrowLeft, Database } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";

import { ErrorState } from "@/components/common/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useIndexNote, useNote, useUpdateNote } from "@/features/notes/api/notes";
import { NoteForm } from "@/features/notes/components/note-form";
import { Link } from "@/i18n/navigation";
import { ApiError } from "@/lib/api/client";

export function NoteDetailView({ noteId }: { noteId: string }) {
  const t = useTranslations("notes");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");
  const format = useFormatter();

  const note = useNote(noteId);
  const updateNote = useUpdateNote(noteId);
  const indexNote = useIndexNote();

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  if (note.isPending) {
    return (
      <div className="grid max-w-3xl gap-4">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (note.isError) {
    return (
      <div className="grid max-w-3xl gap-4">
        <BackLink label={t("detail.back")} />
        <ErrorState error={note.error} onRetry={() => void note.refetch()} />
      </div>
    );
  }

  const indexed = Boolean(note.data.indexedAt);

  return (
    <div className="grid max-w-3xl gap-6">
      <BackLink label={t("detail.back")} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold tracking-tight">
            {note.data.title}
          </h1>
          <p className="text-sm text-muted-foreground">
            {t("detail.updatedAt", {
              date: format.dateTime(new Date(note.data.updatedAt), {
                dateStyle: "medium",
                timeStyle: "short",
              }),
            })}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {indexed ? (
            <Badge variant="secondary" className="font-normal">
              {t("badge.indexed")}
            </Badge>
          ) : (
            <Badge variant="outline" className="font-normal text-muted-foreground">
              {t("badge.notIndexed")}
            </Badge>
          )}
          <Button
            variant="outline"
            size="sm"
            disabled={indexNote.isPending}
            onClick={() =>
              indexNote.mutate(noteId, {
                onSuccess: () => toast.success(t("toast.indexedShort")),
                onError: (error) => toast.error(describe(error)),
              })
            }
          >
            <Database className="size-4" />
            {indexed ? t("detail.reindex") : t("detail.index")}
          </Button>
        </div>
      </div>

      <NoteForm
        defaultValues={{
          title: note.data.title,
          content: note.data.content,
          tags: note.data.tags,
        }}
        submitLabel={tActions("save")}
        pending={updateNote.isPending}
        serverErrors={
          updateNote.error instanceof ApiError ? updateNote.error.fieldErrors : undefined
        }
        onSubmit={(values) =>
          updateNote.mutate(values, {
            // Editing clears indexedAt on the server, so the reminder is not decorative.
            onSuccess: () => toast.success(t("toast.updated")),
            onError: (error) => toast.error(describe(error)),
          })
        }
      />
    </div>
  );
}

function BackLink({ label }: { label: string }) {
  return (
    <Link
      href="/notes"
      className="flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
    >
      <ArrowLeft className="size-4" />
      {label}
    </Link>
  );
}
