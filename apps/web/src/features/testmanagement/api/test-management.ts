"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, http } from "@/lib/api/client";
import type {
  ExternalTestSummary,
  TestManagementBinding,
  TestManagementConnection,
  TestManagementConnectionInput,
  TestManagementVerify,
} from "@/lib/api/types";

export const testManagementKeys = {
  all: ["test-management"] as const,
  connections: (workspaceId: string) =>
    [...testManagementKeys.all, "connections", workspaceId] as const,
  binding: (projectId: string) => [...testManagementKeys.all, "binding", projectId] as const,
  tests: (projectId: string) => [...testManagementKeys.all, "tests", projectId] as const,
};

export function useTestManagementConnections(workspaceId: string | undefined) {
  return useQuery({
    queryKey: testManagementKeys.connections(workspaceId ?? ""),
    queryFn: ({ signal }) =>
      http.get<TestManagementConnection[]>(
        `/api/v1/workspaces/${workspaceId}/test-management-connections`,
        { signal },
      ),
    enabled: Boolean(workspaceId),
  });
}

export function useCreateTestManagementConnection(workspaceId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TestManagementConnectionInput) =>
      http.post<TestManagementConnection>(
        `/api/v1/workspaces/${workspaceId}/test-management-connections`,
        input,
      ),
    onSuccess: () =>
      void qc.invalidateQueries({
        queryKey: testManagementKeys.connections(workspaceId ?? ""),
      }),
  });
}

export function useTestManagementBinding(projectId: string) {
  return useQuery({
    queryKey: testManagementKeys.binding(projectId),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<TestManagementBinding>(
          `/api/v1/projects/${projectId}/test-management`,
          { signal },
        );
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
  });
}

export function useBindTestManagement(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { connectionId: string; remoteProjectId: string }) =>
      http.put<TestManagementBinding>(`/api/v1/projects/${projectId}/test-management`, input),
    onSuccess: (binding) => {
      qc.setQueryData(testManagementKeys.binding(projectId), binding);
      void qc.invalidateQueries({ queryKey: testManagementKeys.tests(projectId) });
    },
  });
}

export function useUnbindTestManagement(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.delete(`/api/v1/projects/${projectId}/test-management`),
    onSuccess: () => {
      qc.setQueryData(testManagementKeys.binding(projectId), null);
      qc.removeQueries({ queryKey: testManagementKeys.tests(projectId) });
    },
  });
}

export function useVerifyTestManagement(projectId: string) {
  return useMutation({
    mutationFn: () =>
      http.get<TestManagementVerify>(`/api/v1/projects/${projectId}/test-management/verify`),
  });
}

export function useExternalTests(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: testManagementKeys.tests(projectId),
    queryFn: ({ signal }) =>
      http.get<ExternalTestSummary[]>(`/api/v1/projects/${projectId}/test-management/tests`, {
        signal,
      }),
    enabled,
  });
}
