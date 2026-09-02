"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, http } from "@/lib/api/client";
import type { FailureAnalysis } from "@/lib/api/types";

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
