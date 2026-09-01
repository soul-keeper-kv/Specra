"use client";

import { useSyncExternalStore } from "react";

import {
  getServerSessionSnapshot,
  getSessionSnapshot,
  subscribeToSession,
} from "@/lib/api/session";
import type { Account } from "@/lib/api/types";

/**
 * React's view of the session that `lib/api/session` owns.
 *
 * `useSyncExternalStore` rather than a Zustand store with `persist`: the fetch layer has to read
 * the same tokens from outside React — it attaches one to every request and exchanges the other on
 * a 401 — and two copies of a credential is one copy too many. The server snapshot is always
 * "signed out, not yet known", which is what the server can honestly render; React swaps in the
 * real value immediately after hydration, with no mismatch and no effect.
 */
export function useSessionSnapshot() {
  return useSyncExternalStore(subscribeToSession, getSessionSnapshot, getServerSessionSnapshot);
}

/** The signed-in account, or null. Null also means "not known yet" — see {@link useSessionReady}. */
export function useSessionUser(): Account | null {
  return useSessionSnapshot().session?.user ?? null;
}

/**
 * False until storage has been read.
 *
 * Anything that redirects a signed-out visitor must wait for this. Without it, the first paint of
 * every page is "signed out", and a signed-in user is bounced to the sign-in screen and back.
 */
export function useSessionReady(): boolean {
  return useSessionSnapshot().hydrated;
}

/** First letters of the display name, for the avatar fallback. */
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}
