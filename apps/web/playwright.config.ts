import { defineConfig, devices } from "@playwright/test";

// Deliberately not 3000: a developer's own `pnpm dev` can keep running while the suite
// executes, and neither server has to be stopped for the other.
const PORT = Number(process.env.E2E_PORT ?? 3100);
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  timeout: 60_000,
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  /**
   * Runs against a production build, not `next dev`.
   *
   * Turbopack compiles a route the first time it is requested, and eight parallel workers
   * hitting a cold dev server race each other over its manifests — the run then fails with
   * `JSON.parse` errors from inside Next itself, which say nothing about the app. A built
   * server has nothing left to compile, so the suite is deterministic, faster per test, and
   * exercises what actually ships.
   *
   * The build is why `timeout` here is generous.
   */
  webServer: {
    command: `pnpm build && pnpm exec next start --port ${PORT}`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 240_000,
  },
});
