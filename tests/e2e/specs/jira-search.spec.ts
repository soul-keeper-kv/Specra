import { expect, test } from "@playwright/test";
import en from "@messages/en.json";
import vi from "@messages/vi.json";
import { signInAsE2eUser } from "../support/session";

for (const [locale, messages] of [
  ["en", en],
  ["vi", vi],
] as const) {
  for (const theme of ["light", "dark"] as const) {
    test(`JQL search, paging and errors (${locale}, ${theme})`, async ({ page }, testInfo) => {
      await signInAsE2eUser(page);
      await page.addInitScript((value) => localStorage.setItem("theme", value), theme);
      const requests: URL[] = [];
      await page.route("**/api/**", async (route) => {
        const url = new URL(route.request().url());
        let body: unknown = {};
        if (url.pathname === "/api/v1/workspaces") {
          body = { content: [{ id: "ws", name: "QA", role: "OWNER" }] };
        } else if (url.pathname === "/api/v1/projects/demo") {
          body = { id: "demo", name: "Jira search QA", key: "QA" };
        } else if (url.pathname.endsWith("/test-management")) {
          body = {
            connectionName: "Jira / Xray",
            provider: "xray",
            remoteProjectId: "QA",
            configuration: { baseUrl: "https://jira.example.com" },
          };
        } else if (url.pathname.endsWith("/test-management/tests")) {
          requests.push(url);
          if (url.searchParams.get("q") === "status =") {
            await route.fulfill({
              status: 400,
              json: {
                status: 400,
                code: "integration-query-invalid",
                title: "Invalid Jira search",
              },
            });
            return;
          }
          const index = Number(url.searchParams.get("page") ?? 0);
          body = {
            content: [
              {
                externalId: `QA-${index + 1}`,
                title: `Login ${index + 1}`,
                status: "Open",
                url: "https://jira.example.com/browse/QA-1",
              },
            ],
            page: index,
            size: 20,
            totalElements: 21,
            totalPages: 2,
            first: index === 0,
            last: index === 1,
          };
        } else {
          await route.abort();
          return;
        }
        await route.fulfill({ json: body });
      });
      await page.goto(`/${locale}/projects/demo/integrations`);
      await expect(page.getByText("Login 1", { exact: true })).toBeVisible();
      await page
        .getByRole("button", { name: messages.testManagement.tests.advanced, exact: true })
        .click();
      const editor = page.getByLabel(messages.testManagement.tests.jqlLabel, { exact: true });
      const query =
        'project = QA AND (status = "Open" OR assignee = currentUser())\nORDER BY updated DESC';
      await editor.fill(query);
      expect(requests.at(-1)?.searchParams.get("q")).toBeNull();
      await editor.press("Control+Enter");
      await expect.poll(() => requests.at(-1)?.searchParams.get("q")).toBe(query);
      expect(requests.at(-1)?.searchParams.get("advanced")).toBe("true");
      await page.getByRole("button", { name: messages.actions.next, exact: true }).click();
      await expect(page.getByText("Login 2", { exact: true })).toBeVisible();
      expect(requests.at(-1)?.searchParams.get("q")).toBe(query);
      expect(requests.at(-1)?.searchParams.get("page")).toBe("1");
      await page.screenshot({ path: testInfo.outputPath("jira-jql.png"), fullPage: true });
      await editor.fill("status =");
      await page.getByRole("button", { name: messages.actions.search, exact: true }).click();
      await expect(
        page.getByRole("alert").filter({ hasText: messages.testManagement.tests.invalidJql }),
      ).toBeVisible();
      expect(requests.at(-1)?.searchParams.get("page")).toBe("0");
      await expect(page.getByText("Login 2", { exact: true })).toHaveCount(0);
      await page
        .getByRole("button", { name: messages.testManagement.tests.clear, exact: true })
        .click();
      await expect(editor).toHaveValue("");
      await expect(page.getByText("Login 1", { exact: true })).toBeVisible();
      await page
        .getByRole("button", { name: messages.testManagement.tests.basic, exact: true })
        .click();
      await page
        .getByRole("textbox", { name: messages.actions.search, exact: true })
        .fill("QA-42");
      await page.getByRole("button", { name: messages.actions.search, exact: true }).click();
      await expect.poll(() => requests.at(-1)?.searchParams.get("q")).toBe("QA-42");
      expect(requests.at(-1)?.searchParams.get("advanced")).toBe("false");
    });
  }
}
