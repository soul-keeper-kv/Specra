"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { ElementUpdateInput, InspectInput, PageObject } from "@/lib/api/types";

export const pageKeys = {
  all: ["page-objects"] as const,
  list: (projectId: string) => [...pageKeys.all, "list", projectId] as const,
  detail: (id: string) => [...pageKeys.all, "detail", id] as const,
};

/** A project's pages, inspected or not. Not paged: the endpoint is not either. */
export function usePageObjects(projectId: string | undefined) {
  return useQuery({
    queryKey: pageKeys.list(projectId ?? ""),
    queryFn: ({ signal }) =>
      http.get<PageObject[]>(`/api/v1/projects/${projectId}/pages`, { signal }),
    enabled: Boolean(projectId),
  });
}

/**
 * Opens a page in a real browser and records what it found.
 *
 * Slow by nature — it launches a browser and loads a page — so the UI shows progress rather than
 * assuming this returns quickly.
 */
export function useInspectPage(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: InspectInput) =>
      http.post<PageObject>(`/api/v1/projects/${projectId}/pages/inspect`, input),
    onSuccess: (page) => {
      queryClient.setQueryData(pageKeys.detail(page.id), page);
      void queryClient.invalidateQueries({ queryKey: pageKeys.list(projectId ?? "") });
    },
  });
}

/** Corrects one locator by hand — the counterpart of editing the IR. */
export function useUpdateElement(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      pageId,
      name,
      input,
    }: {
      pageId: string;
      name: string;
      input: ElementUpdateInput;
    }) => http.put<PageObject>(`/api/v1/pages/${pageId}/elements/${name}`, input),
    onSuccess: (page) => {
      queryClient.setQueryData(pageKeys.detail(page.id), page);
      void queryClient.invalidateQueries({ queryKey: pageKeys.list(projectId ?? "") });
    },
  });
}
