import { expect, test } from "@playwright/test";

/**
 * These run against the web app alone. The API may or may not be up, so assert on
 * things the UI must get right either way — navigation, and the offline affordance.
 */
test("overview renders and links into the app", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Specra", level: 1 })).toBeVisible();
  await expect(page.getByRole("link", { name: "Open notes" })).toBeVisible();

  await page.getByRole("link", { name: "Notes", exact: true }).click();
  await expect(page).toHaveURL(/\/notes$/);
  await expect(page.getByRole("heading", { name: "Notes", level: 1 })).toBeVisible();

  await page.getByRole("link", { name: "Chat", exact: true }).click();
  await expect(page).toHaveURL(/\/chat$/);
  await expect(page.getByRole("heading", { name: "Chat", level: 1 })).toBeVisible();
});

test("new-note dialog validates before it will submit", async ({ page }) => {
  await page.goto("/notes");

  await page.getByRole("button", { name: "New note" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();

  await dialog.getByRole("button", { name: "Create" }).click();
  await expect(dialog.getByText("Title is required")).toBeVisible();
  await expect(dialog.getByText("Content is required")).toBeVisible();
});

test("chat toggles swap the description text", async ({ page }) => {
  await page.goto("/chat");

  await expect(page.getByText(/History is kept in PostgreSQL/)).toBeVisible();
  await page.getByLabel("RAG").click();
  await expect(page.getByText(/grounded in the notes you indexed/)).toBeVisible();
});
