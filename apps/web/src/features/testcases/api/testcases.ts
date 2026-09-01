"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, buildQuery } from "@/lib/api/client";
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
    queryFn: () =>
      apiFetch<PageResponse<TestCaseSummary>>(
        `/api/v1/projects/${projectId}/test-cases${buildQuery({
          q: query.q,
          status: query.status,
          tag: query.tag,
          page: query.page ?? 0,
          size: query.size ?? 20,
          sort: query.sort ?? "updatedAt,desc",
        })}`,
      ),
    placeholderData: (previous) => previous, // keeps the table from flashing while paging
  });
}

export function useTestCase(id: string | undefined) {
  return useQuery({
    queryKey: testCaseKeys.detail(id ?? ""),
    queryFn: () => apiFetch<TestCase>(`/api/v1/test-cases/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateTestCase(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TestCaseInput) =>
      apiFetch<TestCase>(`/api/v1/projects/${projectId}/test-cases`, {
        method: "POST",
        body: input,
      }),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

export function useUpdateTestCase(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: TestCaseInput) =>
      apiFetch<TestCase>(`/api/v1/test-cases/${id}`, { method: "PUT", body: input }),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

export function useDeleteTestCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/test-cases/${id}`, { method: "DELETE" }),
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
    mutationFn: (id: string) =>
      apiFetch<TestCase>(`/api/v1/test-cases/${id}/index`, { method: "POST" }),
    onSuccess: (testCase) => {
      qc.setQueryData(testCaseKeys.detail(testCase.id), testCase);
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}
