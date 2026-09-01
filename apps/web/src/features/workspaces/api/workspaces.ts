"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { PageResponse, Workspace, WorkspaceInput } from "@/lib/api/types";

export const workspaceKeys = {
  all: ["workspaces"] as const,
  list: () => [...workspaceKeys.all, "list"] as const,
};

export function useWorkspaces() {
  return useQuery({
    queryKey: workspaceKeys.list(),
    queryFn: ({ signal }) =>
      http.get<PageResponse<Workspace>>("/api/v1/workspaces", { signal, params: { size: 50 } }),
  });
}

export function useCreateWorkspace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: WorkspaceInput) => http.post<Workspace>("/api/v1/workspaces", input),
    onSuccess: () => void qc.invalidateQueries({ queryKey: workspaceKeys.list() }),
  });
}

/**
 * The workspace everything currently shown belongs to.
 *
 * M1 has no workspace switcher — most installations have exactly one — so "active" simply means
 * the first one, and a `null` workspace with `isPending: false` means none exists yet and the UI
 * should offer to create it. When a switcher arrives, the choice moves into a store and this hook
 * is the only place that has to learn about it.
 */
export function useActiveWorkspace() {
  const workspaces = useWorkspaces();
  return {
    ...workspaces,
    workspace: workspaces.data?.content[0] ?? null,
  };
}
