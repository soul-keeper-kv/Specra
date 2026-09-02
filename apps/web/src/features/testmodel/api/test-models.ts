"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { testCaseKeys } from "@/features/testcases/api/testcases";
import { ApiError, http } from "@/lib/api/client";
import type { TestModel, TestModelVersionSummary } from "@/lib/api/types";

export const testModelKeys = {
  all: ["testmodels"] as const,
  current: (testCaseId: string) => [...testModelKeys.all, "current", testCaseId] as const,
  versions: (testCaseId: string) => [...testModelKeys.all, "versions", testCaseId] as const,
};

/** The current IR, or null while none has been derived yet — a state the UI renders, not an error. */
export function useTestModel(testCaseId: string | undefined) {
  return useQuery({
    queryKey: testModelKeys.current(testCaseId ?? ""),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<TestModel>(`/api/v1/test-cases/${testCaseId}/model`, { signal });
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

export function useTestModelVersions(testCaseId: string | undefined) {
  return useQuery({
    queryKey: testModelKeys.versions(testCaseId ?? ""),
    queryFn: ({ signal }) =>
      http.get<TestModelVersionSummary[]>(`/api/v1/test-cases/${testCaseId}/model/versions`, {
        signal,
      }),
    enabled: Boolean(testCaseId),
  });
}

/**
 * Asks AI to derive the next Test Model version. A 422 comes back as an `ApiError` whose
 * `problem.questions` (test-case-ambiguous) or `problem.violations` (test-model-invalid) the
 * workspace lays beside the steps they are about.
 */
export function useModelTestCase(testCaseId: string | undefined) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<TestModel>(`/api/v1/test-cases/${testCaseId}/model`),
    onSuccess: (model) => {
      qc.setQueryData(testModelKeys.current(model.testCaseId), model);
      void qc.invalidateQueries({ queryKey: testModelKeys.versions(model.testCaseId) });
      // The case itself moved to MODELLED and lost its out-of-date flag.
      void qc.invalidateQueries({ queryKey: testCaseKeys.detail(model.testCaseId) });
      void qc.invalidateQueries({ queryKey: testCaseKeys.lists() });
    },
  });
}
