import { expect, test } from "@playwright/test";

import en from "../src/messages/en.json";
import vi from "../src/messages/vi.json";

/**
 * These run against the web app alone. The API may or may not be up, so assert on things the UI
 * must get right either way — routing, language, theme and client-side validation.
 *
 * Expected text is imported from the bundles rather than written out here: a copy of a string in a
 * test is a second translation that nobody remembers to update.
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

test("the workspace navigates between its sections", async ({ page }) => {
  await page.goto("/en/dashboard");

  await expect(page.getByRole("heading", { name: en.dashboard.title, level: 1 })).toBeVisible();

  await page.getByRole("link", { name: en.nav.notes, exact: true }).click();
  await expect(page).toHaveURL(/\/en\/notes$/);
  await expect(page.getByRole("heading", { name: en.notes.title, level: 1 })).toBeVisible();

  await page.getByRole("link", { name: en.nav.chat, exact: true }).click();
  await expect(page).toHaveURL(/\/en\/chat$/);
  await expect(page.getByRole("heading", { name: en.chat.title, level: 1 })).toBeVisible();
});

test("switching language rewrites the path and keeps the page", async ({ page }) => {
  await page.goto("/en/notes");

  await page.getByRole("button", { name: en.language.change }).click();
  await page.getByRole("menuitem", { name: "Tiếng Việt" }).click();

  await expect(page).toHaveURL(/\/vi\/notes$/);
  await expect(page.getByRole("heading", { name: vi.notes.title, level: 1 })).toBeVisible();
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

test("the new-note dialog validates before it will submit", async ({ page }) => {
  await page.goto("/en/notes");

  await page.getByRole("button", { name: en.notes.new.trigger }).first().click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();

  await dialog.getByRole("button", { name: en.notes.new.submit }).click();
  await expect(dialog.getByText(en.notes.validation.titleRequired)).toBeVisible();
  await expect(dialog.getByText(en.notes.validation.contentRequired)).toBeVisible();
});

test("the chat toggles swap the description text", async ({ page }) => {
  await page.goto("/en/chat");

  await expect(page.getByText(en.chat.subtitle)).toBeVisible();
  await page.getByLabel(en.chat.rag).click();
  await expect(page.getByText(en.chat.subtitleRag)).toBeVisible();
});
