"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { http } from "@/lib/api/client";
import {
  clearSession,
  getSession,
  sessionFromTokens,
  setSession,
  updateSessionUser,
} from "@/lib/api/session";
import type {
  Account,
  AuthSession,
  AuthTokens,
  ChangePasswordInput,
  LoginInput,
  ProfileInput,
  RegisterInput,
} from "@/lib/api/types";

export const authKeys = {
  all: ["auth"] as const,
  me: () => [...authKeys.all, "me"] as const,
  sessions: () => [...authKeys.all, "sessions"] as const,
};

/**
 * Signing in or out changes what every other query is allowed to see, so the whole cache is
 * dropped rather than selectively invalidated. Anything left behind would be the previous
 * account's data rendered under the new one's name.
 */
function useSessionReset() {
  const qc = useQueryClient();
  return () => qc.clear();
}

export function useLogin() {
  const reset = useSessionReset();
  return useMutation({
    mutationFn: (input: LoginInput) => http.post<AuthTokens>("/api/v1/auth/login", input),
    onSuccess: (tokens) => {
      reset();
      setSession(sessionFromTokens(tokens));
    },
  });
}

export function useRegister() {
  const reset = useSessionReset();
  return useMutation({
    mutationFn: (input: RegisterInput) => http.post<AuthTokens>("/api/v1/auth/register", input),
    onSuccess: (tokens) => {
      reset();
      setSession(sessionFromTokens(tokens));
    },
  });
}

/**
 * Tells the API to revoke this device's refresh token, then forgets it locally.
 *
 * The local half happens either way: a sign-out that fails because the network is down must still
 * sign the user out of this browser, which is the thing they actually asked for.
 */
export function useLogout() {
  const reset = useSessionReset();
  return useMutation({
    mutationFn: async () => {
      const refreshToken = getSession()?.refreshToken;
      if (!refreshToken) return;
      try {
        await http.post("/api/v1/auth/logout", { refreshToken });
      } catch {
        // Already revoked, or unreachable. Neither changes what happens next.
      }
    },
    onSettled: () => {
      clearSession();
      reset();
    },
  });
}

/** The account as the server sees it, for screens that must not trust a stale stored copy. */
export function useAccount(enabled = true) {
  return useQuery({
    queryKey: authKeys.me(),
    queryFn: ({ signal }) => http.get<Account>("/api/v1/auth/me", { signal }),
    enabled,
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ProfileInput) => http.patch<Account>("/api/v1/auth/me", input),
    onSuccess: (account) => {
      qc.setQueryData(authKeys.me(), account);
      updateSessionUser(account);
    },
  });
}

/** Every other device is signed out, and this one is handed a fresh pair. */
export function useChangePassword() {
  return useMutation({
    mutationFn: (input: ChangePasswordInput) =>
      http.post<AuthTokens>("/api/v1/auth/change-password", input),
    onSuccess: (tokens) => setSession(sessionFromTokens(tokens)),
  });
}

export function useAuthSessions() {
  return useQuery({
    queryKey: authKeys.sessions(),
    queryFn: ({ signal }) => {
      const refreshToken = getSession()?.refreshToken;
      return http.get<AuthSession[]>("/api/v1/auth/sessions", {
        signal,
        // Lets the API mark which row is this browser, without putting a long-lived
        // credential in a query string where every proxy would log it.
        headers: refreshToken ? { "X-Refresh-Token": refreshToken } : undefined,
      });
    },
  });
}

export function useRevokeSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete(`/api/v1/auth/sessions/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: authKeys.sessions() }),
  });
}
