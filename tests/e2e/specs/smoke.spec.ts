import { expect, test } from "@playwright/test";

import en from "@messages/en.json";
import vi from "@messages/vi.json";

import { signInAsE2eUser } from "../support/session";

/**
 * These run against the web app alone. The API may or may not be up, so assert on things the UI
 * must get right either way — routing, language, theme and client-side validation.
 *
 * Expected text is imported from the bundles rather than written out here: a copy of a string in a
 * test is a second translation that nobody remembers to update. The `@messages/*` alias in
 * tsconfig.json is the only path from this package into apps/web, and it is deliberately narrow —
 * e2e drives the product through its interface, it does not reach into its source.
 */

/**
 * The bare root is never rendered: the proxy redirects it to a locale. Which one depends on the
 * visitor's Accept-Language, so both directions are asserted — a single assertion here would pass
 * or fail depending on the language the machine running the tests happens to be set to.
 */
test.describe("locale detection at the root", () => {
  test.describe("a Vietnamese browser", () => {
    test.use({ locale: "vi-VN" });

    test("lands on /vi", async ({ page }) => {
      await page.goto("/");

      await expect(page).toHaveURL(/\/vi$/);
      await expect(
        page.getByRole("heading", { name: vi.marketing.title, level: 1 }),
      ).toBeVisible();
    });
  });

  test.describe("an English browser", () => {
    test.use({ locale: "en-GB" });

    test("lands on /en", async ({ page }) => {
      await page.goto("/");

      await expect(page).toHaveURL(/\/en$/);
      await expect(
        page.getByRole("heading", { name: en.marketing.title, level: 1 }),
      ).toBeVisible();
    });
  });
});

/**
 * The signed-in shell redirects a visitor with no session, so these seed one before navigating.
 * The marketing and sign-in tests below deliberately do not — they are about the signed-out side.
 */
test.describe("the signed-in shell", () => {
  test.beforeEach(async ({ page }) => {
    await signInAsE2eUser(page);
  });

  test("navigates between its sections", async ({ page }) => {
    await page.goto("/en/dashboard");

    await expect(
      page.getByRole("heading", { name: en.dashboard.title, level: 1 }),
    ).toBeVisible();

    await page.getByRole("link", { name: en.nav.projects, exact: true }).click();
    await expect(page).toHaveURL(/\/en\/projects$/);
    await expect(
      page.getByRole("heading", { name: en.projects.title, level: 1 }),
    ).toBeVisible();

    await page.getByRole("link", { name: en.nav.chat, exact: true }).click();
    await expect(page).toHaveURL(/\/en\/chat$/);
    await expect(page.getByRole("heading", { name: en.chat.title, level: 1 })).toBeVisible();
  });

  test("switching language rewrites the path and keeps the page", async ({ page }) => {
    await page.goto("/en/projects");

    await page.getByRole("button", { name: en.language.change }).click();
    await page.getByRole("menuitem", { name: "Tiếng Việt" }).click();

    await expect(page).toHaveURL(/\/vi\/projects$/);
    await expect(
      page.getByRole("heading", { name: vi.projects.title, level: 1 }),
    ).toBeVisible();
  });

  test("the theme choice reaches the html element", async ({ page }) => {
    await page.goto("/en/dashboard");

    await page.getByRole("button", { name: en.theme.change }).click();
    await page.getByRole("menuitem", { name: en.theme.dark }).click();

    await expect(page.locator("html")).toHaveClass(/dark/);
  });

  test("the command palette opens on the keyboard and navigates", async ({ page }) => {
    await page.goto("/en/dashboard");

    await page.keyboard.press("ControlOrMeta+k");
    const palette = page.getByRole("dialog");
    await expect(palette).toBeVisible();

    await palette.getByRole("option", { name: en.nav.chat }).click();
    await expect(page).toHaveURL(/\/en\/chat$/);
  });

  test("the chat toggles swap the description text", async ({ page }) => {
    await page.goto("/en/chat");

    await expect(page.getByText(en.chat.subtitle)).toBeVisible();
    await page.getByLabel(en.chat.rag).click();
    await expect(page.getByText(en.chat.subtitleRag)).toBeVisible();
  });
});

test("the sign-in form validates before it will submit", async ({ page }) => {
  await page.goto("/en/sign-in");

  // Both fields left empty on purpose: a non-empty invalid email would be swallowed by the
  // browser's native type="email" validation before the zod messages ever render.
  await page.getByRole("button", { name: en.auth.signIn.submit }).click();

  await expect(page.getByText(en.auth.validation.emailRequired)).toBeVisible();
  await expect(page.getByText(en.auth.validation.passwordRequired)).toBeVisible();
});

test("a signed-out visitor is sent from the shell to sign in, and can reach sign-up", async ({
  page,
}) => {
  await page.goto("/en/dashboard");

  // The guard keeps where they were going, so signing in resumes it instead of dumping them
  // on the dashboard.
  await expect(page).toHaveURL(/\/en\/sign-in\?next=%2Fdashboard$/);
  // By text rather than by role: a shadcn CardTitle is a styled div, not a heading, and asserting
  // a role the markup does not claim would be testing our own guess about the component. `exact`
  // is what keeps it to the card — Next's route announcer repeats the page title too.
  await expect(page.getByText(en.auth.signIn.title, { exact: true })).toBeVisible();

  await page.getByRole("link", { name: en.auth.signIn.signUpLink }).click();
  await expect(page).toHaveURL(/\/en\/sign-up$/);
  await expect(page.getByText(en.auth.signUp.title, { exact: true })).toBeVisible();
});
