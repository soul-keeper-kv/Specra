"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { testCaseKeys } from "@/features/testcases/api/testcases";
import { ApiError, http } from "@/lib/api/client";
import type {
  ExternalTestQuery,
  ExternalTestSummary,
  ExternalTestDetail,
  PageResponse,
  TestCase,
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
  testList: (projectId: string, query: ExternalTestQuery) =>
    [...testManagementKeys.tests(projectId), "list", query] as const,
  test: (projectId: string, externalId: string) =>
    [...testManagementKeys.tests(projectId), externalId] as const,
  importedTestCase: (projectId: string, externalId: string) =>
    [...testManagementKeys.test(projectId, externalId), "test-case"] as const,
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

export function useExternalTests(
  projectId: string,
  query: ExternalTestQuery,
  enabled: boolean,
) {
  return useQuery({
    queryKey: testManagementKeys.testList(projectId, query),
    queryFn: ({ signal }) =>
      http.get<PageResponse<ExternalTestSummary>>(
        `/api/v1/projects/${projectId}/test-management/tests`,
        {
          signal,
          // The search runs against the provider, not against Specra — a blank q would be sent
          // as an empty JQL term, so it is dropped rather than passed through.
          params: {
            q: query.q || undefined,
            advanced: query.advanced,
            page: query.page ?? 0,
            size: query.size ?? 20,
          },
        },
      ),
    enabled,
  });
}

export function useExternalTest(projectId: string, externalId: string) {
  return useQuery({
    queryKey: testManagementKeys.test(projectId, externalId),
    queryFn: ({ signal }) =>
      http.get<ExternalTestDetail>(
        `/api/v1/projects/${projectId}/test-management/tests/${encodeURIComponent(externalId)}`,
        { signal },
      ),
  });
}

/** The Specra case this external test was imported as, or null while it has not been — a state. */
export function useImportedTestCase(projectId: string, externalId: string) {
  return useQuery({
    queryKey: testManagementKeys.importedTestCase(projectId, externalId),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<TestCase>(
          `/api/v1/projects/${projectId}/test-management/tests/${encodeURIComponent(externalId)}/test-case`,
          { signal },
        );
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });
}

export function useImportExternalTest(projectId: string, externalId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      http.post<TestCase>(
        `/api/v1/projects/${projectId}/test-management/tests/${encodeURIComponent(externalId)}/import`,
      ),
    onSuccess: (testCase) => {
      qc.setQueryData(testManagementKeys.importedTestCase(projectId, externalId), testCase);
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}
