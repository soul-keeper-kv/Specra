import type { Account, AuthTokens } from "./types";

/**
 * The signed-in session, held outside React.
 *
 * <p>It lives here rather than in a Zustand store because the transport needs it: the axios
 * interceptors in `lib/api/client` attach the access token to every request and reach the refresh
 * token on a 401, and an interceptor is not a component and cannot call a hook. Components read
 * the same value through
 * `useSession()` in `features/auth/store`, which is a `useSyncExternalStore` over this module — one
 * source of truth, two ways in.
 *
 * The tokens are kept in `localStorage`. That is a deliberate trade: an httpOnly cookie would
 * survive XSS better, but it needs the API and the web app to share a site, which they do not here
 * (`:3000` and `:8080`, and different hosts in production). The mitigations that remain are the
 * ones that matter most anyway — a short access-token life, a rotating refresh token, and reuse
 * detection on the server.
 */
export type Session = {
  user: Account;
  accessToken: string;
  /** ISO instant. Read to refresh slightly early rather than waiting for a 401. */
  expiresAt: string;
  refreshToken: string;
  refreshExpiresAt: string;
};

export type SessionSnapshot = {
  session: Session | null;
  /**
   * False until `localStorage` has been read. Anything that redirects on "signed out" must wait
   * for this, or the first paint sends a signed-in user to the sign-in page.
   */
  hydrated: boolean;
};

const STORAGE_KEY = "specra.session";

/** Stable identity, so `useSyncExternalStore` does not loop on the server. */
const SERVER_SNAPSHOT: SessionSnapshot = { session: null, hydrated: false };

let snapshot: SessionSnapshot = SERVER_SNAPSHOT;
let loaded = false;
const listeners = new Set<() => void>();

/**
 * Reads storage once, lazily. Not at module scope: this file is imported by `client.ts`, which is
 * reachable from a server render where `localStorage` does not exist.
 */
function ensureLoaded(): void {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  snapshot = { session: read(), hydrated: true };
}

function read(): Session | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    // A half-written or older shape is not a session; treat it as signed out rather than
    // letting `undefined` reach an Authorization header.
    return parsed?.accessToken && parsed?.refreshToken && parsed?.user ? parsed : null;
  } catch {
    return null;
  }
}

export function getSessionSnapshot(): SessionSnapshot {
  ensureLoaded();
  return snapshot;
}

export function getServerSessionSnapshot(): SessionSnapshot {
  return SERVER_SNAPSHOT;
}

export function getSession(): Session | null {
  return getSessionSnapshot().session;
}

export function subscribeToSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Replaces the whole session, or clears it. Every writer goes through here. */
export function setSession(session: Session | null): void {
  loaded = true;
  snapshot = { session, hydrated: true };

  if (typeof window !== "undefined") {
    try {
      if (session) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // A private window with storage disabled still gets a working session for this tab;
      // it simply will not survive a reload.
    }
  }

  listeners.forEach((listener) => listener());
}

export function clearSession(): void {
  setSession(null);
}

/** What login, register, refresh and change-password all return, stored as one value. */
export function sessionFromTokens(tokens: AuthTokens): Session {
  return {
    user: tokens.user,
    accessToken: tokens.accessToken,
    expiresAt: tokens.expiresAt,
    refreshToken: tokens.refreshToken,
    refreshExpiresAt: tokens.refreshExpiresAt,
  };
}

/** Keeps the profile in step after a rename, without disturbing the tokens. */
export function updateSessionUser(user: Account): void {
  const current = getSession();
  if (current) setSession({ ...current, user });
}
