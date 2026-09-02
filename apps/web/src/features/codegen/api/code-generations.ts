"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { testCaseKeys } from "@/features/testcases/api/testcases";
import { ApiError, http } from "@/lib/api/client";
import type { ApplyGenerationInput, CodeGeneration } from "@/lib/api/types";

export const codeGenerationKeys = {
  all: ["codegenerations"] as const,
  current: (testCaseId: string) => [...codeGenerationKeys.all, "current", testCaseId] as const,
  history: (testCaseId: string) => [...codeGenerationKeys.all, "history", testCaseId] as const,
};

/**
 * The proposal awaiting review, or null when there is none.
 *
 * Null rather than an error: "nothing has been generated yet" is the ordinary opening state of
 * the screen, and rendering it as a failure would be a lie about a perfectly fine situation.
 */
export function useCodeGeneration(testCaseId: string | undefined) {
  return useQuery({
    queryKey: codeGenerationKeys.current(testCaseId ?? ""),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<CodeGeneration>(`/api/v1/test-cases/${testCaseId}/code`, {
          signal,
        });
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled: Boolean(testCaseId),
  });
}

export function useCodeGenerationHistory(testCaseId: string | undefined) {
  return useQuery({
    queryKey: codeGenerationKeys.history(testCaseId ?? ""),
    queryFn: ({ signal }) =>
      http.get<CodeGeneration[]>(`/api/v1/test-cases/${testCaseId}/code/history`, { signal }),
    enabled: Boolean(testCaseId),
  });
}

/** Projects the current Test Model into files. Writes nothing to the repository. */
export function useGenerateCode(testCaseId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<CodeGeneration>(`/api/v1/test-cases/${testCaseId}/code`),
    onSuccess: (generation) => {
      qc.setQueryData(codeGenerationKeys.current(generation.testCaseId), generation);
      void qc.invalidateQueries({
        queryKey: codeGenerationKeys.history(generation.testCaseId),
      });
    },
  });
}

/** The one call that puts generated code in a repository, and only a person makes it. */
export function useApplyGeneration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input?: ApplyGenerationInput }) =>
      http.post<CodeGeneration>(`/api/v1/code-generations/${id}/apply`, input ?? {}),
    onSuccess: (generation) => {
      // The case is COMMITTED now, and the proposal is no longer awaiting anything.
      qc.setQueryData(codeGenerationKeys.current(generation.testCaseId), null);
      void qc.invalidateQueries({
        queryKey: codeGenerationKeys.history(generation.testCaseId),
      });
      void qc.invalidateQueries({ queryKey: testCaseKeys.detail(generation.testCaseId) });
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}

export function useRejectGeneration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      http.post<CodeGeneration>(`/api/v1/code-generations/${id}/reject`),
    onSuccess: (generation) => {
      qc.setQueryData(codeGenerationKeys.current(generation.testCaseId), null);
      void qc.invalidateQueries({
        queryKey: codeGenerationKeys.history(generation.testCaseId),
      });
    },
  });
}
