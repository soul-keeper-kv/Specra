"use client";

import { ArrowLeft, Database } from "lucide-react";
import Link from "next/link";
import { use } from "react";
import { toast } from "sonner";

import { NoteForm } from "@/components/notes/note-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/client";
import { useIndexNote, useNote, useUpdateNote } from "@/lib/api/notes";

export default function NoteDetailPage(props: PageProps<"/notes/[id]">) {
  // Next 16 hands params to the page as a promise; `use` unwraps it in a client component.
  const { id } = use(props.params);

  const note = useNote(id);
  const updateNote = useUpdateNote(id);
  const indexNote = useIndexNote();

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
        <BackLink />
        <p className="rounded-lg border p-6 text-sm text-destructive">
          {note.error instanceof ApiError ? note.error.message : "Something went wrong"}
        </p>
      </div>
    );
  }

  return (
    <div className="grid max-w-3xl gap-6">
      <BackLink />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold tracking-tight">
            {note.data.title}
          </h1>
          <p className="text-sm text-muted-foreground">
            Updated {new Date(note.data.updatedAt).toLocaleString()}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {note.data.indexedAt ? (
            <Badge variant="secondary" className="font-normal">
              in pgvector
            </Badge>
          ) : (
            <Badge variant="outline" className="font-normal text-muted-foreground">
              not indexed
            </Badge>
          )}
          <Button
            variant="outline"
            size="sm"
            disabled={indexNote.isPending}
            onClick={() =>
              indexNote.mutate(id, {
                onSuccess: () => toast.success("Indexed into pgvector"),
                onError: (error) =>
                  toast.error(error instanceof ApiError ? error.message : "Indexing failed"),
              })
            }
          >
            <Database className="size-4" />
            {note.data.indexedAt ? "Re-index" : "Index"}
          </Button>
        </div>
      </div>

      <NoteForm
        defaultValues={{
          title: note.data.title,
          content: note.data.content,
          tags: note.data.tags,
        }}
        submitLabel="Save changes"
        pending={updateNote.isPending}
        serverErrors={
          updateNote.error instanceof ApiError ? updateNote.error.fieldErrors : undefined
        }
        onSubmit={(values) =>
          updateNote.mutate(values, {
            onSuccess: () => toast.success("Saved. Re-index to refresh what RAG sees."),
            onError: (error) =>
              toast.error(error instanceof ApiError ? error.message : "Save failed"),
          })
        }
      />
    </div>
  );
}

function BackLink() {
  return (
    <Link
      href="/notes"
      className="flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
    >
      <ArrowLeft className="size-4" />
      All notes
    </Link>
  );
}
