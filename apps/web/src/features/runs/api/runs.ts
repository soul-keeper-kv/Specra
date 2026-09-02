"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import type { ArtifactLink, PageResponse, Run, RunInput } from "@/lib/api/types";

export const runKeys = {
  all: ["runs"] as const,
  lists: () => [...runKeys.all, "list"] as const,
  list: (projectId: string) => [...runKeys.lists(), projectId] as const,
  detail: (id: string) => [...runKeys.all, "detail", id] as const,
  artifacts: (itemId: string) => [...runKeys.all, "artifacts", itemId] as const,
};

/**
 * How often a run in flight is re-fetched.
 *
 * Polling rather than a stream, and that is settled rather than pending. The runner's `execute`
 * job is one blocking call, so a run changes state exactly twice — queued to running, then
 * running to its result — and a stream would deliver the same two transitions this catches, in
 * exchange for an emitter registry and disconnect handling on both sides.
 *
 * Worth revisiting when the runner emits per-cell progress; 05-api-contracts.md carries the
 * fuller reasoning.
 */
const IN_FLIGHT_POLL_MS = 3000;

function isInFlight(run: Run | undefined): boolean {
  return run?.status === "QUEUED" || run?.status === "RUNNING";
}

export function useRuns(projectId: string | undefined) {
  return useQuery({
    queryKey: runKeys.list(projectId ?? ""),
    queryFn: ({ signal }) =>
      http.get<PageResponse<Run>>(`/api/v1/projects/${projectId}/runs`, { signal }),
    enabled: Boolean(projectId),
    // Keeps the list moving while anything on it is still going.
    refetchInterval: (query) =>
      query.state.data?.content.some(isInFlight) ? IN_FLIGHT_POLL_MS : false,
  });
}

export function useRun(id: string | undefined) {
  return useQuery({
    queryKey: runKeys.detail(id ?? ""),
    queryFn: ({ signal }) => http.get<Run>(`/api/v1/runs/${id}`, { signal }),
    enabled: Boolean(id),
    refetchInterval: (query) => (isInFlight(query.state.data) ? IN_FLIGHT_POLL_MS : false),
  });
}

/** Queues a run. Answers 202 with the queued run, not with a result. */
export function useRequestRun(projectId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RunInput) =>
      http.post<Run>(`/api/v1/projects/${projectId}/runs`, input),
    onSuccess: (run) => {
      queryClient.setQueryData(runKeys.detail(run.id), run);
      void queryClient.invalidateQueries({ queryKey: runKeys.list(projectId ?? "") });
    },
  });
}

export function useCancelRun() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.post<Run>(`/api/v1/runs/${id}/cancel`),
    onSuccess: (run) => {
      queryClient.setQueryData(runKeys.detail(run.id), run);
      void queryClient.invalidateQueries({ queryKey: runKeys.list(run.projectId) });
    },
  });
}

/**
 * Evidence links for one matrix cell.
 *
 * Not cached beyond the screen: every link expires, and a stale one from a previous visit would
 * be a broken download rather than a fast one. `enabled` keeps it from firing until a user
 * actually opens the cell.
 */
export function useArtifactLinks(itemId: string | undefined) {
  return useQuery({
    queryKey: runKeys.artifacts(itemId ?? ""),
    queryFn: ({ signal }) =>
      http.get<ArtifactLink[]>(`/api/v1/run-items/${itemId}/artifacts`, { signal }),
    enabled: Boolean(itemId),
    staleTime: 0,
    gcTime: 0,
  });
}
