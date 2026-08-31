"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, buildQuery } from "./client";
import type { Note, NoteInput, NoteQuery, PageResponse } from "./types";

export const noteKeys = {
  all: ["notes"] as const,
  lists: () => [...noteKeys.all, "list"] as const,
  list: (query: NoteQuery) => [...noteKeys.lists(), query] as const,
  details: () => [...noteKeys.all, "detail"] as const,
  detail: (id: string) => [...noteKeys.details(), id] as const,
};

export function useNotes(query: NoteQuery) {
  return useQuery({
    queryKey: noteKeys.list(query),
    queryFn: () =>
      apiFetch<PageResponse<Note>>(
        `/api/notes${buildQuery({
          q: query.q,
          tag: query.tag,
          page: query.page ?? 0,
          size: query.size ?? 10,
          sort: query.sort ?? "updatedAt,desc",
        })}`,
      ),
    placeholderData: (previous) => previous, // keeps the table from flashing while paging
  });
}

export function useNote(id: string | undefined) {
  return useQuery({
    queryKey: noteKeys.detail(id ?? ""),
    queryFn: () => apiFetch<Note>(`/api/notes/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: NoteInput) =>
      apiFetch<Note>("/api/notes", { method: "POST", body: input }),
    onSuccess: (note) => {
      qc.setQueryData(noteKeys.detail(note.id), note);
      void qc.invalidateQueries({ queryKey: noteKeys.lists() });
    },
  });
}

export function useUpdateNote(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: NoteInput) =>
      apiFetch<Note>(`/api/notes/${id}`, { method: "PUT", body: input }),
    onSuccess: (note) => {
      qc.setQueryData(noteKeys.detail(note.id), note);
      void qc.invalidateQueries({ queryKey: noteKeys.lists() });
    },
  });
}

export function useDeleteNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/api/notes/${id}`, { method: "DELETE" }),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: noteKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: noteKeys.lists() });
    },
  });
}

/** Pushes the note's chunks into pgvector so RAG can retrieve them. */
export function useIndexNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<Note>(`/api/notes/${id}/index`, { method: "POST" }),
    onSuccess: (note) => {
      qc.setQueryData(noteKeys.detail(note.id), note);
      void qc.invalidateQueries({ queryKey: noteKeys.lists() });
    },
  });
}
