"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, buildQuery } from "@/lib/api/client";
import type {
  PageResponse,
  Project,
  ProjectInput,
  ProjectPatch,
  ProjectQuery,
} from "@/lib/api/types";

export const projectKeys = {
  all: ["projects"] as const,
  lists: () => [...projectKeys.all, "list"] as const,
  list: (workspaceId: string, query: ProjectQuery) =>
    [...projectKeys.lists(), workspaceId, query] as const,
  details: () => [...projectKeys.all, "detail"] as const,
  detail: (id: string) => [...projectKeys.details(), id] as const,
};

export function useProjects(workspaceId: string | undefined, query: ProjectQuery) {
  return useQuery({
    queryKey: projectKeys.list(workspaceId ?? "", query),
    queryFn: () =>
      apiFetch<PageResponse<Project>>(
        `/api/v1/workspaces/${workspaceId}/projects${buildQuery({
          q: query.q,
          page: query.page ?? 0,
          size: query.size ?? 20,
          sort: query.sort ?? "updatedAt,desc",
        })}`,
      ),
    enabled: Boolean(workspaceId),
    placeholderData: (previous) => previous, // keeps the grid from flashing while paging
  });
}

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: projectKeys.detail(id ?? ""),
    queryFn: () => apiFetch<Project>(`/api/v1/projects/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateProject(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectInput) =>
      apiFetch<Project>(`/api/v1/workspaces/${workspaceId}/projects`, {
        method: "POST",
        body: input,
      }),
    onSuccess: (project) => {
      qc.setQueryData(projectKeys.detail(project.id), project);
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}

export function useUpdateProject(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectPatch) =>
      apiFetch<Project>(`/api/v1/projects/${id}`, { method: "PATCH", body: input }),
    onSuccess: (project) => {
      qc.setQueryData(projectKeys.detail(project.id), project);
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}

export function useDeleteProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/api/v1/projects/${id}`, { method: "DELETE" }),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: projectKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}
