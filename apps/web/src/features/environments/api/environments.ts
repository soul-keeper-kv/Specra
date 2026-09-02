"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { Environment, EnvironmentInput } from "@/lib/api/types";

export const environmentKeys = {
  all: ["environments"] as const,
  lists: () => [...environmentKeys.all, "list"] as const,
  list: (projectId: string) => [...environmentKeys.lists(), projectId] as const,
  detail: (id: string) => [...environmentKeys.all, "detail", id] as const,
};

/**
 * A project's environments.
 *
 * Not paged, because the endpoint is not: a project has a handful of these, and unwrapping a
 * `PageResponse` around three rows would be ceremony on both sides.
 */
export function useEnvironments(projectId: string | undefined) {
  return useQuery({
    queryKey: environmentKeys.list(projectId ?? ""),
    queryFn: ({ signal }) =>
      http.get<Environment[]>(`/api/v1/projects/${projectId}/environments`, { signal }),
    enabled: Boolean(projectId),
  });
}

export function useCreateEnvironment(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: EnvironmentInput) =>
      http.post<Environment>(`/api/v1/projects/${projectId}/environments`, input),
    onSuccess: (environment) => {
      queryClient.setQueryData(environmentKeys.detail(environment.id), environment);
      // The whole list, not just the new row: creating a default clears whichever held it.
      void queryClient.invalidateQueries({ queryKey: environmentKeys.list(projectId ?? "") });
    },
  });
}

export function useUpdateEnvironment(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: EnvironmentInput }) =>
      http.put<Environment>(`/api/v1/environments/${id}`, input),
    onSuccess: (environment) => {
      queryClient.setQueryData(environmentKeys.detail(environment.id), environment);
      void queryClient.invalidateQueries({ queryKey: environmentKeys.list(projectId ?? "") });
    },
  });
}

export function useDeleteEnvironment(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete(`/api/v1/environments/${id}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: environmentKeys.list(projectId ?? "") });
    },
  });
}
