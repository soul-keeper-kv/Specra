"use client";

import type { SortingState } from "@tanstack/react-table";
import { Loader2, NotebookPen, Plus, Search } from "lucide-react";
import { useTranslations } from "next-intl";
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
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCreateNote,
  useDeleteNote,
  useIndexNote,
  useNotes,
} from "@/features/notes/api/notes";
import { NoteForm } from "@/features/notes/components/note-form";
import { NotesTable } from "@/features/notes/components/notes-table";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { ApiError } from "@/lib/api/client";
import type { Note } from "@/lib/api/types";

const PAGE_SIZE = 10;

export function NotesView() {
  const t = useTranslations("notes");
  const tActions = useTranslations("actions");
  const tErrors = useTranslations("errors");

  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "updatedAt", desc: true }]);
  const [createOpen, setCreateOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Note | null>(null);

  // Keystrokes should not each become a request.
  const debouncedSearch = useDebouncedValue(search);

  const sort = sorting[0]
    ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
    : "updatedAt,desc";

  const notes = useNotes({
    q: debouncedSearch || undefined,
    page,
    size: PAGE_SIZE,
    sort,
  });
  const createNote = useCreateNote();
  const deleteNote = useDeleteNote();
  const indexNote = useIndexNote();

  const describe = (error: unknown) =>
    error instanceof ApiError ? error.message : tErrors("generic");

  function confirmDelete(note: Note) {
    deleteNote.mutate(note.id, {
      onSuccess: () => toast.success(t("toast.deleted", { title: note.title })),
      onError: (error) => toast.error(describe(error)),
      onSettled: () => setPendingDelete(null),
    });
  }

  function handleIndex(note: Note) {
    indexNote.mutate(note.id, {
      onSuccess: () => toast.success(t("toast.indexed", { title: note.title })),
      onError: (error) => toast.error(describe(error)),
    });
  }

  const isEmpty = notes.data?.content.length === 0;

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
            <DialogContent className="sm:max-w-2xl">
              <DialogHeader>
                <DialogTitle>{t("new.title")}</DialogTitle>
                <DialogDescription>{t("new.description")}</DialogDescription>
              </DialogHeader>
              <NoteForm
                submitLabel={t("new.submit")}
                pending={createNote.isPending}
                serverErrors={
                  createNote.error instanceof ApiError
                    ? createNote.error.fieldErrors
                    : undefined
                }
                onSubmit={(values) =>
                  createNote.mutate(values, {
                    onSuccess: (note) => {
                      toast.success(t("toast.created", { title: note.title }));
                      setCreateOpen(false);
                    },
                    onError: (error) => toast.error(describe(error)),
                  })
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
            // A narrower result set may not have a page 4; start over rather than land on
            // an empty table. Done here rather than in an effect on the debounced value,
            // because it belongs to the keystroke, not to a later render.
            setPage(0);
          }}
          placeholder={t("searchPlaceholder")}
          aria-label={tActions("search")}
          className="pl-9"
        />
      </div>

      {notes.isPending ? (
        <div className="grid gap-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-12 w-full" />
          ))}
        </div>
      ) : notes.isError ? (
        <ErrorState error={notes.error} onRetry={() => void notes.refetch()} />
      ) : isEmpty ? (
        <EmptyState
          icon={NotebookPen}
          title={t("table.empty")}
          description={t("table.emptyHint")}
          action={
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="size-4" />
              {t("new.trigger")}
            </Button>
          }
        />
      ) : (
        <>
          <NotesTable
            notes={notes.data.content}
            sorting={sorting}
            onSortingChange={setSorting}
            onDelete={setPendingDelete}
            onIndex={handleIndex}
            indexingId={indexNote.isPending ? indexNote.variables : undefined}
          />

          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {t("count", { count: notes.data.totalElements })}
              {notes.isFetching ? (
                <Loader2 className="ml-2 inline size-3 animate-spin" />
              ) : null}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={notes.data.first}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                {tActions("previous")}
              </Button>
              <span className="text-muted-foreground tabular-nums">
                {t("pagination", {
                  page: notes.data.page + 1,
                  total: Math.max(1, notes.data.totalPages),
                })}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={notes.data.last}
                onClick={() => setPage((current) => current + 1)}
              >
                {tActions("next")}
              </Button>
            </div>
          </div>
        </>
      )}

      {/* Deleting also drops the note's embeddings, so it is worth one confirmation. */}
      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => (open ? null : setPendingDelete(null))}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmDelete.title")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("confirmDelete.description", { title: pendingDelete?.title ?? "" })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tActions("cancel")}</AlertDialogCancel>
            <AlertDialogAction
              disabled={deleteNote.isPending}
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
