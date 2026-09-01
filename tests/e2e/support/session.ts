import type { Page } from "@playwright/test";

/**
 * Puts a session in the browser before the app boots.
 *
 * The signed-in shell now redirects a visitor with no session to the sign-in page, and these tests
 * run with the API deliberately out of the picture — so there is nobody to sign in with. Writing
 * the same `localStorage` key the app reads gets past the route guard without a running backend;
 * every request the pages then make still fails, which is exactly what the assertions here already
 * expect (they cover routing, language, theme and client-side validation, never data).
 *
 * The access token is a plausible-looking string and nothing more. It is never verified in these
 * tests, because no request is ever answered.
 */
const STORAGE_KEY = "specra.session";

const FAKE_SESSION = {
  user: {
    id: "00000000-0000-4000-8000-000000000001",
    email: "e2e@specra.dev",
    displayName: "E2E Runner",
    status: "ACTIVE",
    locale: null,
    lastLoginAt: null,
    createdAt: "2026-01-01T00:00:00Z",
  },
  accessToken: "e2e.not-a-real-token",
  expiresAt: "2099-01-01T00:00:00Z",
  refreshToken: "e2e-refresh",
  refreshExpiresAt: "2099-01-01T00:00:00Z",
};

export async function signInAsE2eUser(page: Page): Promise<void> {
  await page.addInitScript(
    ([key, value]) => {
      window.localStorage.setItem(key, value);
    },
    [STORAGE_KEY, JSON.stringify(FAKE_SESSION)] as const,
  );
}
