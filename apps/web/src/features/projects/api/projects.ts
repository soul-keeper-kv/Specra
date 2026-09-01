"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
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
    queryFn: ({ signal }) =>
      http.get<PageResponse<Project>>(`/api/v1/workspaces/${workspaceId}/projects`, {
        signal,
        // An empty filter is left off the query string entirely — axios drops an `undefined`
        // param, and the API reads a blank `q` as "match nothing".
        params: {
          q: query.q || undefined,
          page: query.page ?? 0,
          size: query.size ?? 20,
          sort: query.sort ?? "updatedAt,desc",
        },
      }),
    enabled: Boolean(workspaceId),
    placeholderData: (previous) => previous, // keeps the grid from flashing while paging
  });
}

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: projectKeys.detail(id ?? ""),
    queryFn: ({ signal }) => http.get<Project>(`/api/v1/projects/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useCreateProject(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectInput) =>
      http.post<Project>(`/api/v1/workspaces/${workspaceId}/projects`, input),
    onSuccess: (project) => {
      qc.setQueryData(projectKeys.detail(project.id), project);
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}

export function useUpdateProject(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProjectPatch) => http.patch<Project>(`/api/v1/projects/${id}`, input),
    onSuccess: (project) => {
      qc.setQueryData(projectKeys.detail(project.id), project);
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}

export function useDeleteProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete(`/api/v1/projects/${id}`),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: projectKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: projectKeys.lists() });
    },
  });
}
