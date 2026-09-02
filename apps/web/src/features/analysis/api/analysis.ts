"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { codeGenerationKeys } from "@/features/codegen/api/code-generations";
import { ApiError, http } from "@/lib/api/client";
import type { CodeGeneration, FailureAnalysis } from "@/lib/api/types";

export const analysisKeys = {
  all: ["failure-analysis"] as const,
  item: (itemId: string) => [...analysisKeys.all, itemId] as const,
};

/**
 * The stored reading for one failed cell, if it has one.
 *
 * A 404 is the ordinary answer — most failures have not been analysed — so it resolves to null
 * rather than becoming an error state the user has to read past. Anything else still throws.
 */
export function useFailureAnalysis(itemId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: analysisKeys.item(itemId ?? ""),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<FailureAnalysis>(`/api/v1/run-items/${itemId}/analysis`, {
          signal,
        });
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      }
    },
    enabled: Boolean(itemId) && enabled,
    // The evidence cannot change once the run has finished, and neither can the reading of it.
    staleTime: Infinity,
  });
}

/**
 * Asks for an analysis.
 *
 * Idempotent on the server: without `reanalyse` a second press returns the stored reading rather
 * than paying for a second opinion on evidence that cannot have changed.
 */
export function useAnalyseFailure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, reanalyse }: { itemId: string; reanalyse?: boolean }) =>
      http.post<FailureAnalysis>(
        `/api/v1/run-items/${itemId}/analysis${reanalyse ? "?reanalyse=true" : ""}`,
      ),
    onSuccess: (analysis) => {
      queryClient.setQueryData(analysisKeys.item(analysis.testRunItemId), analysis);
    },
  });
}

/**
 * Asks for a patch, which arrives as an ordinary PROPOSED code generation.
 *
 * Nothing is written by this call. The proposal is reviewed and applied on the test case's script
 * tab, through the same apply as any other — which is why the case's `current` proposal is
 * invalidated here rather than the result being rendered in place.
 */
export function useProposeRepair() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (analysisId: string) =>
      http.post<CodeGeneration>(`/api/v1/failure-analyses/${analysisId}/repair`),
    onSuccess: (generation) => {
      queryClient.setQueryData(codeGenerationKeys.current(generation.testCaseId), generation);
      void queryClient.invalidateQueries({
        queryKey: codeGenerationKeys.history(generation.testCaseId),
      });
    },
  });
}
