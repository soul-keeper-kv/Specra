"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError, http } from "@/lib/api/client";
import type {
  GitBranchInput,
  GitCommit,
  GitCommitInfo,
  GitCommitInput,
  GitFileContent,
  GitRepository,
  GitRepositoryInput,
  GitStatus,
  GitVerifyResult,
  PageResponse,
} from "@/lib/api/types";

export const gitKeys = {
  all: ["git"] as const,
  repository: (projectId: string) => [...gitKeys.all, "repository", projectId] as const,
  status: (projectId: string) => [...gitKeys.all, "status", projectId] as const,
  branches: (projectId: string) => [...gitKeys.all, "branches", projectId] as const,
  diff: (projectId: string, path?: string) =>
    [...gitKeys.all, "diff", projectId, path] as const,
  history: (projectId: string, page: number) =>
    [...gitKeys.all, "history", projectId, page] as const,
  file: (projectId: string, path: string) => [...gitKeys.all, "file", projectId, path] as const,
};

/**
 * Resolves to `null` when the project has no repository yet.
 *
 * The API answers that with `repository-not-connected`, a 409 — a state the Source Control panel
 * renders as its first step, not a failure to retry at the user.
 */
export function useRepository(projectId: string) {
  return useQuery({
    queryKey: gitKeys.repository(projectId),
    queryFn: async ({ signal }) => {
      try {
        return await http.get<GitRepository>(`/api/v1/projects/${projectId}/repository`, {
          signal,
        });
      } catch (error) {
        if (error instanceof ApiError && error.code === "repository-not-connected") {
          return null;
        }
        throw error;
      }
    },
  });
}

export function useConnectRepository(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: GitRepositoryInput) =>
      http.put<GitRepository>(`/api/v1/projects/${projectId}/repository`, input),
    onSuccess: (repository) => {
      qc.setQueryData(gitKeys.repository(projectId), repository);
      // The working copy was re-cloned, so every read of it is stale.
      void qc.invalidateQueries({ queryKey: gitKeys.all });
    },
  });
}

export function useDisconnectRepository(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.delete(`/api/v1/projects/${projectId}/repository`),
    onSuccess: () => {
      qc.setQueryData(gitKeys.repository(projectId), null);
      void qc.invalidateQueries({ queryKey: gitKeys.all });
    },
  });
}

/** Asks the remote itself; a success means the credential really works. */
export function useVerifyRepository(projectId: string) {
  return useMutation({
    mutationFn: () =>
      http.post<GitVerifyResult>(`/api/v1/projects/${projectId}/repository/verify`),
  });
}

export function useGitStatus(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: gitKeys.status(projectId),
    queryFn: ({ signal }) =>
      http.get<GitStatus>(`/api/v1/projects/${projectId}/git/status`, { signal }),
    enabled,
  });
}

export function useGitBranches(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: gitKeys.branches(projectId),
    queryFn: ({ signal }) =>
      http.get<string[]>(`/api/v1/projects/${projectId}/git/branches`, { signal }),
    enabled,
  });
}

/** Plain text, not JSON: a unified diff is its own format and the viewer renders it as one. */
export function useGitDiff(projectId: string, path: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: gitKeys.diff(projectId, path),
    queryFn: ({ signal }) =>
      http.get<string>(`/api/v1/projects/${projectId}/git/diff`, {
        signal,
        params: { path: path || undefined },
        // Without this axios parses a diff that happens to start with { as JSON.
        responseType: "text",
        transformResponse: [(data: string) => data],
      }),
    enabled,
  });
}

export function useGitHistory(projectId: string, page: number, enabled: boolean) {
  return useQuery({
    queryKey: gitKeys.history(projectId, page),
    queryFn: ({ signal }) =>
      http.get<PageResponse<GitCommitInfo>>(`/api/v1/projects/${projectId}/git/history`, {
        signal,
        params: { page, size: 20 },
      }),
    enabled,
    placeholderData: (previous) => previous,
  });
}

export function useGitFile(projectId: string, path: string | undefined) {
  return useQuery({
    queryKey: gitKeys.file(projectId, path ?? ""),
    queryFn: ({ signal }) =>
      http.get<GitFileContent>(`/api/v1/projects/${projectId}/git/file`, {
        signal,
        params: { path },
      }),
    enabled: Boolean(path),
  });
}

export function useWriteGitFile(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: GitFileContent) =>
      http.put<GitFileContent>(`/api/v1/projects/${projectId}/git/file`, input),
    onSuccess: (file) => {
      qc.setQueryData(gitKeys.file(projectId, file.path), file);
      void qc.invalidateQueries({ queryKey: gitKeys.status(projectId) });
      void qc.invalidateQueries({ queryKey: gitKeys.diff(projectId, undefined) });
    },
  });
}

export function useCommit(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: GitCommitInput) =>
      http.post<GitCommit>(`/api/v1/projects/${projectId}/git/commit`, input),
    onSuccess: () => invalidateWorkingCopy(qc, projectId),
  });
}

export function usePush(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<GitStatus>(`/api/v1/projects/${projectId}/git/push`),
    onSuccess: (status) => {
      qc.setQueryData(gitKeys.status(projectId), status);
    },
  });
}

export function usePull(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<GitStatus>(`/api/v1/projects/${projectId}/git/pull`),
    onSuccess: () => invalidateWorkingCopy(qc, projectId),
  });
}

export function useCreateBranch(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: GitBranchInput) =>
      http.post<GitStatus>(`/api/v1/projects/${projectId}/git/branches`, input),
    onSuccess: () => invalidateWorkingCopy(qc, projectId),
  });
}

export function useCheckout(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (branch: string) =>
      http.post<GitStatus>(`/api/v1/projects/${projectId}/git/checkout`, { branch }),
    onSuccess: () => invalidateWorkingCopy(qc, projectId),
  });
}

/**
 * Everything read out of the working copy at once: a commit, a pull, a branch switch all change
 * the files, the status, the diff and the history together, and refreshing one of the four while
 * the others sit stale is how a panel starts contradicting itself.
 */
function invalidateWorkingCopy(qc: ReturnType<typeof useQueryClient>, projectId: string): void {
  void qc.invalidateQueries({ queryKey: gitKeys.all });
  void qc.invalidateQueries({ queryKey: gitKeys.repository(projectId) });
}
