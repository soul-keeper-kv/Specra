"use client";

import type { SortingState } from "@tanstack/react-table";
import { Loader2, Plus, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { NoteForm } from "@/components/notes/note-form";
import { NotesTable } from "@/components/notes/notes-table";
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
import { ApiError } from "@/lib/api/client";
import { useCreateNote, useDeleteNote, useIndexNote, useNotes } from "@/lib/api/notes";
import type { Note } from "@/lib/api/types";

const PAGE_SIZE = 10;

export default function NotesPage() {
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [sorting, setSorting] = useState<SortingState>([{ id: "updatedAt", desc: true }]);
  const [open, setOpen] = useState(false);

  // Keystrokes should not each become a request.
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const sort = sorting[0]
    ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
    : "updatedAt,desc";

  const notes = useNotes({ q: debounced || undefined, page, size: PAGE_SIZE, sort });
  const createNote = useCreateNote();
  const deleteNote = useDeleteNote();
  const indexNote = useIndexNote();

  const handleDelete = (note: Note) => {
    deleteNote.mutate(note.id, {
      onSuccess: () => toast.success(`Deleted "${note.title}"`),
      onError: (error) => toast.error(describe(error)),
    });
  };

  const handleIndex = (note: Note) => {
    indexNote.mutate(note.id, {
      onSuccess: () => toast.success(`"${note.title}" is now searchable by RAG`),
      onError: (error) => toast.error(describe(error)),
    });
  };

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold tracking-tight">Notes</h1>
          <p className="text-sm text-muted-foreground">
            Stored in PostgreSQL. Index one to make it retrievable from pgvector.
          </p>
        </div>

        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="size-4" />
              New note
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-2xl">
            <DialogHeader>
              <DialogTitle>New note</DialogTitle>
              <DialogDescription>
                Validated by Zod here and by bean validation on the API.
              </DialogDescription>
            </DialogHeader>
            <NoteForm
              submitLabel="Create"
              pending={createNote.isPending}
              serverErrors={
                createNote.error instanceof ApiError ? createNote.error.fieldErrors : undefined
              }
              onSubmit={(values) =>
                createNote.mutate(values, {
                  onSuccess: (note) => {
                    toast.success(`Created "${note.title}"`);
                    setOpen(false);
                  },
                  onError: (error) => toast.error(describe(error)),
                })
              }
            />
          </DialogContent>
        </Dialog>
      </div>

      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search title and content…"
          className="pl-9"
        />
      </div>

      {notes.isPending ? (
        <div className="grid gap-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : notes.isError ? (
        <p className="rounded-lg border p-6 text-sm text-destructive">
          {describe(notes.error)}
        </p>
      ) : (
        <>
          <NotesTable
            notes={notes.data.content}
            sorting={sorting}
            onSortingChange={setSorting}
            onDelete={handleDelete}
            onIndex={handleIndex}
            indexingId={indexNote.isPending ? indexNote.variables : undefined}
          />

          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {notes.data.totalElements} note(s)
              {notes.isFetching ? (
                <Loader2 className="ml-2 inline size-3 animate-spin" />
              ) : null}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={notes.data.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <span className="text-muted-foreground tabular-nums">
                {notes.data.page + 1} / {Math.max(1, notes.data.totalPages)}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={notes.data.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function describe(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong";
}
