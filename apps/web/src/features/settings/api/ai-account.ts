"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, http } from "@/lib/api/client";
import type { AiAccount, AiAccountInput } from "@/lib/api/types";

export const aiAccountKeys = {
  all: ["ai-account"] as const,
  detail: (workspaceId: string) => [...aiAccountKeys.all, workspaceId] as const,
};

/**
 * Resolves to `null` when the workspace has no account — running on the platform default is a
 * state the settings screen renders, not an error to retry.
 */
export function useAiAccount(workspaceId: string | undefined) {
  return useQuery({
    queryKey: aiAccountKeys.detail(workspaceId ?? ""),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<AiAccount>(`/api/v1/workspaces/${workspaceId}/ai-account`, {
          signal,
        });
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled: Boolean(workspaceId),
  });
}

export function useSaveAiAccount(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: AiAccountInput) =>
      http.put<AiAccount>(`/api/v1/workspaces/${workspaceId}/ai-account`, input),
    onSuccess: (account) => {
      qc.setQueryData(aiAccountKeys.detail(account.workspaceId), account);
    },
  });
}

export function useDeleteAiAccount(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.delete(`/api/v1/workspaces/${workspaceId}/ai-account`),
    onSuccess: () => {
      qc.setQueryData(aiAccountKeys.detail(workspaceId ?? ""), null);
    },
  });
}
