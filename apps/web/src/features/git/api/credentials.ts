"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { GitCredential, GitCredentialInput } from "@/lib/api/types";

export const gitCredentialKeys = {
  all: ["git-credentials"] as const,
  list: (workspaceId: string) => [...gitCredentialKeys.all, workspaceId] as const,
};

export function useGitCredentials(workspaceId: string | undefined) {
  return useQuery({
    queryKey: gitCredentialKeys.list(workspaceId ?? ""),
    queryFn: ({ signal }) =>
      http.get<GitCredential[]>(`/api/v1/workspaces/${workspaceId}/git-credentials`, {
        signal,
      }),
    enabled: Boolean(workspaceId),
  });
}

export function useCreateGitCredential(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: GitCredentialInput) =>
      http.post<GitCredential>(`/api/v1/workspaces/${workspaceId}/git-credentials`, input),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: gitCredentialKeys.list(workspaceId ?? "") }),
  });
}

export function useDeleteGitCredential(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      http.delete(`/api/v1/workspaces/${workspaceId}/git-credentials/${id}`),
    onSuccess: () =>
      void qc.invalidateQueries({ queryKey: gitCredentialKeys.list(workspaceId ?? "") }),
  });
}
