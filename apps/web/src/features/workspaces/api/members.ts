"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type {
  Member,
  MemberAddInput,
  PageResponse,
  RoleInfo,
  WorkspaceRole,
} from "@/lib/api/types";

export const memberKeys = {
  all: ["members"] as const,
  list: (workspaceId: string) => [...memberKeys.all, workspaceId] as const,
  roles: () => ["roles"] as const,
};

export function useMembers(workspaceId: string | undefined) {
  return useQuery({
    queryKey: memberKeys.list(workspaceId ?? ""),
    queryFn: ({ signal }) =>
      http.get<PageResponse<Member>>(`/api/v1/workspaces/${workspaceId}/members`, {
        signal,
        params: { size: 100 },
      }),
    enabled: Boolean(workspaceId),
  });
}

/**
 * The roles and what each one carries, straight from the API.
 *
 * Fetched rather than hard-coded so a screen greys out a button for the same reason the API would
 * refuse it. It is a constant of the deployment, so it is cached for the life of the tab.
 */
export function useRoles() {
  return useQuery({
    queryKey: memberKeys.roles(),
    queryFn: ({ signal }) => http.get<RoleInfo[]>("/api/v1/roles", { signal }),
    staleTime: Infinity,
  });
}

export function useAddMember(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: MemberAddInput) =>
      http.post<Member>(`/api/v1/workspaces/${workspaceId}/members`, input),
    onSuccess: () => void qc.invalidateQueries({ queryKey: memberKeys.list(workspaceId) }),
  });
}

export function useChangeMemberRole(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: WorkspaceRole }) =>
      http.put<Member>(`/api/v1/workspaces/${workspaceId}/members/${userId}/role`, { role }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: memberKeys.list(workspaceId) }),
  });
}

export function useRemoveMember(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) =>
      http.delete(`/api/v1/workspaces/${workspaceId}/members/${userId}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: memberKeys.list(workspaceId) }),
  });
}
