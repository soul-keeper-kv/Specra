"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type {
  PageResponse,
  TestCase,
  TestCaseInput,
  TestCaseQuery,
  TestCaseSummary,
} from "@/lib/api/types";

export const testCaseKeys = {
  all: ["testcases"] as const,
  lists: () => [...testCaseKeys.all, "list"] as const,
  list: (projectId: string, query: TestCaseQuery) =>
    [...testCaseKeys.lists(), projectId, query] as const,
  details: () => [...testCaseKeys.all, "detail"] as const,
  detail: (id: string) => [...testCaseKeys.details(), id] as const,
};

export function useTestCases(projectId: string, query: TestCaseQuery) {
  return useQuery({
    queryKey: testCaseKeys.list(projectId, query),
    queryFn: ({ signal }) =>
      http.get<PageResponse<TestCaseSummary>>(`/api/v1/projects/${projectId}/test-cases`, {
        signal,
        // An unset filter is left off the query string entirely — axios drops an `undefined`
        // param, and the API reads a blank one as "match nothing".
        params: {
          q: query.q || undefined,
          status: query.status || undefined,
          tag: query.tag || undefined,
          page: query.page ?? 0,
          size: query.size ?? 20,
          sort: query.sort ?? "updatedAt,desc",
        },
      }),
    placeholderData: (previous) => previous, // keeps the table from flashing while paging
  });
}

export function useTestCase(id: string | undefined) {
  return useQuery({
    queryKey: testCaseKeys.detail(id ?? ""),
    queryFn: ({ signal }) => http.get<TestCase>(`/api/v1/test-cases/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

export function useCreateTestCase(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TestCaseInput) =>
      http.post<TestCase>(`/api/v1/projects/${projectId}/test-cases`, input),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

export function useUpdateTestCase(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TestCaseInput) => http.put<TestCase>(`/api/v1/test-cases/${id}`, input),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

export function useDeleteTestCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete(`/api/v1/test-cases/${id}`),
    onSuccess: (_data, id) => {
      qc.removeQueries({ queryKey: testCaseKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

/** Pushes the case's text into pgvector so the assistant can retrieve and cite it. */
export function useIndexTestCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.post<TestCase>(`/api/v1/test-cases/${id}/index`),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}
