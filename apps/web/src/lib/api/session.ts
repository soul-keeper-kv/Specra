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
 *
 * <p>This module also owns *how a session ends*. Clearing it is not enough on its own: the reason
 * it ended decides whether the user is told anything ("you were signed out because your session
 * expired") and whether the other open tabs follow. Both live here, next to the value, so no
 * caller has to remember to do them.
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

/**
 * Why the session ended, when it ended by itself.
 *
 * Only "expired" is worth interrupting a user for. A deliberate sign-out needs no explanation, and
 * "revoked" — the refresh token was rejected while it should still have been valid — is the one
 * that means something happened on the server: a password change elsewhere, an administrator, or
 * replay detection retiring the whole chain.
 */
export type SessionEndedReason = "expired" | "revoked" | "signed-out";

const STORAGE_KEY = "specra.session";

/**
 * Refresh this far before the access token is actually due.
 *
 * A request that leaves with a token expiring in two seconds can still arrive after it has died —
 * clock skew between browser and server is the usual cause, and it is exactly the case where a
 * user sees a spurious error on a session that was fine.
 */
const EXPIRY_SKEW_MS = 30_000;

/** Stable identity, so `useSyncExternalStore` does not loop on the server. */
const SERVER_SNAPSHOT: SessionSnapshot = { session: null, hydrated: false };

let snapshot: SessionSnapshot = SERVER_SNAPSHOT;
let loaded = false;
let endedReason: SessionEndedReason | null = null;
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

  // A new session cancels any explanation the previous one left behind: signing back in is
  // exactly the acknowledgement that "your session expired" was waiting for.
  if (session) endedReason = null;

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

  notify();
}

/**
 * Ends the session, recording why.
 *
 * Everything that signs a user out goes through here rather than assigning null, so the sign-in
 * page can say what happened. The reason is deliberately not persisted: it is about *this*
 * navigation, and a message about a session that ended last Tuesday helps nobody.
 */
export function endSession(reason: SessionEndedReason = "signed-out"): void {
  const wasSignedIn = getSession() !== null;
  setSession(null);
  // After setSession, which clears it — the order matters.
  endedReason = wasSignedIn && reason !== "signed-out" ? reason : null;
  notify();
}

export function clearSession(): void {
  endSession("signed-out");
}

/** Read once by the sign-in screen, to explain an arrival it did not ask for. */
export function getSessionEndedReason(): SessionEndedReason | null {
  return endedReason;
}

export function consumeSessionEndedReason(): SessionEndedReason | null {
  const reason = endedReason;
  endedReason = null;
  return reason;
}

function notify(): void {
  listeners.forEach((listener) => listener());
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

/** True when `at` is in the past, allowing for the skew a network round-trip adds. */
function isPast(at: string | undefined, skewMs: number): boolean {
  if (!at) return true;
  const time = Date.parse(at);
  // An unparseable instant is not evidence of a live token; treat it as spent.
  return Number.isNaN(time) || time - skewMs <= Date.now();
}

/**
 * True when the access token is spent, or close enough that sending it would be a wasted call.
 *
 * The transport asks this before every request, which is what turns an expired token into a
 * refresh instead of into a 401 the user waits for.
 */
export function isAccessTokenExpired(session: Session | null = getSession()): boolean {
  return session === null || isPast(session.expiresAt, EXPIRY_SKEW_MS);
}

/**
 * True when the refresh token is spent too — the session cannot be revived, and the only honest
 * thing left is to sign the user out.
 *
 * No skew here: unlike the access token there is no follow-up call to protect, and shortening a
 * long-lived credential by half a minute only signs people out early.
 */
export function isRefreshTokenExpired(session: Session | null = getSession()): boolean {
  return session === null || isPast(session.refreshExpiresAt, 0);
}

/** Milliseconds until the session can no longer be refreshed. Never negative. */
export function millisUntilSessionEnds(session: Session | null = getSession()): number {
  if (!session) return 0;
  const time = Date.parse(session.refreshExpiresAt);
  if (Number.isNaN(time)) return 0;
  return Math.max(0, time - Date.now());
}

/**
 * Keeps every tab of this browser on the same session.
 *
 * `storage` fires in the *other* tabs, not the one that wrote — which is precisely the case that
 * needs handling: signing out in one tab must not leave a second tab showing a dashboard it can no
 * longer load, and signing in again must not leave it stuck on the sign-in screen. The value is
 * re-read from storage rather than trusted from the event, so one code path parses it.
 *
 * Registered at module scope on purpose: the transport is imported long before any component
 * mounts, and a session that changes before React starts still has to be noticed.
 */
if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key !== null && event.key !== STORAGE_KEY) return;

    const next = read();
    if (next?.accessToken === getSession()?.accessToken) return;

    loaded = true;
    snapshot = { session: next, hydrated: true };
    // A tab that lost its session did not do so of its own accord; "expired" is the honest
    // reading, and the tab that actually signed out has already shown its own confirmation.
    if (!next) endedReason = "expired";
    notify();
  });
}
