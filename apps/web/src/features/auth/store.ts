"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type SessionUser = {
  name: string;
  email: string;
};

/**
 * A stand-in session, kept entirely in the browser.
 *
 * The API has no auth yet, so this exists to give the shell something real to render — a user menu,
 * a sign-out, a route group that sends you to the sign-in page. When real auth arrives, the shape
 * below is what the components already consume, so only this file and the sign-in form change.
 */
type AuthState = {
  user: SessionUser | null;
  /**
   * False until zustand has read localStorage. Anything that depends on `user` must wait for this,
   * or the server renders "signed out", the client immediately renders "signed in", and React
   * reports a hydration mismatch.
   */
  hydrated: boolean;
  signIn: (user: SessionUser) => void;
  signOut: () => void;
  update: (patch: Partial<SessionUser>) => void;
  setHydrated: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      hydrated: false,
      signIn: (user) => set({ user }),
      signOut: () => set({ user: null }),
      update: (patch) =>
        set((state) => (state.user ? { user: { ...state.user, ...patch } } : state)),
      setHydrated: () => set({ hydrated: true }),
    }),
    {
      name: "specra-session",
      // Only the user is stored; `hydrated` describes this tab, not the saved session.
      partialize: (state) => ({ user: state.user }),
      onRehydrateStorage: () => (state) => state?.setHydrated(),
    },
  ),
);

/** Most components only need these two, and reading them separately avoids re-rendering on writes. */
export const useSessionUser = () => useAuthStore((state) => state.user);
export const useSessionReady = () => useAuthStore((state) => state.hydrated);

/** First letters of the display name, for the avatar fallback. */
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}
