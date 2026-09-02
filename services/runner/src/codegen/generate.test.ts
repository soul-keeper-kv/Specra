/**
 * What the projection must be true about, beyond matching yesterday's bytes.
 *
 * A golden file catches a change; these catch a change that is wrong — a secret in the output,
 * a guessed locator, a duration, an engine word in a file that must not have one.
 */

import { describe, expect, it } from "vitest";
import type { TestModel } from "@specra/test-model";

import { CHECKOUT_PAGES, LOGIN_FLOW, LOGIN_PAGES, irFixture, options } from "./fixtures.js";
import { generate } from "./generate.js";
import { configFileName, engineTestPackage } from "../adapters/playwright/index.js";
import { docComment, kebabCase, lineComment, specPath, stringLiteral } from "./naming.js";

const login = () => irFixture("login");

function fileNamed(files: { path: string; contents: string }[], suffix: string): string {
  const found = files.find((file) => file.path.endsWith(suffix));
  if (!found) {
    throw new Error(
      `No generated file ends with ${suffix}; got ${files.map((f) => f.path).join(", ")}`,
    );
  }
  return found.contents;
}

describe("generate", () => {
  it("puts the spec, the pages and the IR where 07-git.md says they go", () => {
    const { files } = generate({
      model: login(),
      pages: LOGIN_PAGES,
      options: options({ reference: "TC-104", area: "auth" }),
    });

    expect(files.map((file) => file.path)).toEqual([
      ".specra/models/TC-104.json",
      "fixtures/environment.ts",
      "pages/DashboardPage.ts",
      "pages/LoginPage.ts",
      "tests/auth/login-with-valid-credentials.spec.ts",
    ]);
  });

  it("commits the IR beside the code, unchanged", () => {
    const model = login();
    const { files } = generate({ model, pages: LOGIN_PAGES, options: options() });

    const committed = JSON.parse(fileNamed(files, ".specra/models/TC-1.json")) as TestModel;
    expect(committed).toEqual(model);
  });

  it("never writes a secret's value into the repository — only its name", () => {
    const { files } = generate({
      model: irFixture("checkout"),
      pages: CHECKOUT_PAGES,
      flows: [{ name: "login", model: LOGIN_FLOW }],
      options: options({ reference: "TC-77" }),
    });

    const everything = files.map((file) => file.contents).join("\n");
    // The IR carries the name; the value is supplied at dispatch and must appear nowhere.
    expect(everything).toContain("cardNumber");
    expect(everything).toContain("CARD_NUMBER");
    expect(everything).not.toMatch(/4[0-9]{12}/);
    // The accessor reads the environment; nothing inlines a credential.
    expect(fileNamed(files, "fixtures/environment.ts")).toContain("process.env[name]");
  });

  it("waits for a state, never for a duration", () => {
    const { files } = generate({
      model: irFixture("checkout"),
      pages: CHECKOUT_PAGES,
      flows: [
        { name: "login", model: LOGIN_FLOW },
        { name: "clearCart", model: { ...LOGIN_FLOW, name: "Empty the cart" } },
      ],
      options: options(),
    });

    const spec = fileNamed(files, ".spec.ts");
    expect(spec).not.toMatch(/waitForTimeout|setTimeout|sleep\(/);
  });

  it("reports a page nobody inspected instead of guessing a locator", () => {
    const { unresolved, files } = generate({
      model: login(),
      // DashboardPage deliberately missing: the last assertion targets it.
      pages: LOGIN_PAGES.filter((page) => page.name !== "DashboardPage"),
      options: options(),
    });

    expect(unresolved).toEqual([{ stepId: "s5", page: "DashboardPage", element: "heading" }]);
    // And it did not invent one on the way past.
    expect(fileNamed(files, ".spec.ts")).not.toContain("#dashboard");
  });

  it("keeps a raw selector inline, where a reviewer sees the debt", () => {
    const { files } = generate({
      model: irFixture("checkout"),
      pages: CHECKOUT_PAGES,
      flows: [
        { name: "login", model: LOGIN_FLOW },
        { name: "clearCart", model: { ...LOGIN_FLOW, name: "Empty the cart" } },
      ],
      options: options(),
    });

    expect(fileNamed(files, ".spec.ts")).toContain("#promo .handle");
  });

  it("refuses a document the schema rejects rather than emitting source for it", () => {
    const broken = { ...login(), steps: [] } as unknown as TestModel;

    expect(() => generate({ model: broken, pages: LOGIN_PAGES, options: options() })).toThrow(
      /not valid/,
    );
  });

  it("scaffolds a project that stands on its own", () => {
    const { files } = generate({
      model: login(),
      pages: LOGIN_PAGES,
      options: options({ scaffold: true, projectName: "acme-e2e" }),
    });

    const pkg = JSON.parse(fileNamed(files, "package.json")) as {
      name: string;
      scripts: Record<string, string>;
      devDependencies: Record<string, string>;
    };
    expect(pkg.name).toBe("acme-e2e");
    expect(pkg.scripts.test).toBe("playwright test");
    expect(Object.keys(pkg.devDependencies)).toContain(engineTestPackage());
    expect(pkg.devDependencies[engineTestPackage()]).toMatch(/^\^1\./);

    // Nothing at test time may call Specra, or read .specra/.
    const runtime = files
      .filter((file) => file.path.endsWith(".ts") || file.path === configFileName())
      .map((file) => file.contents)
      .join("\n");
    expect(runtime).not.toMatch(/specra\.dev|localhost:8080|\.specra\//i);
  });

  it("describes itself in .specra/project.json without anything depending on it", () => {
    const { files } = generate({
      model: login(),
      pages: LOGIN_PAGES,
      options: options({ scaffold: true, adapterVersion: "0.1.0" }),
    });

    const descriptor = JSON.parse(fileNamed(files, ".specra/project.json")) as {
      engine: string;
      adapterVersion: string;
    };
    expect(descriptor).toMatchObject({ engine: "playwright", adapterVersion: "0.1.0" });
  });
});

describe("naming", () => {
  it("is stable, ascii and locale-independent", () => {
    expect(kebabCase("Đăng nhập với tài khoản hợp lệ")).toBe("dang-nhap-voi-tai-khoan-hop-le");
    expect(kebabCase("Login with valid credentials")).toBe("login-with-valid-credentials");
    expect(kebabCase("  ")).toBe("test");
    expect(specPath("Login", "auth")).toBe("tests/auth/login.spec.ts");
    expect(specPath("Login")).toBe("tests/login.spec.ts");
  });

  it("escapes anything that could end a string literal early", () => {
    expect(stringLiteral('a "quoted" \\ value')).toBe('"a \\"quoted\\" \\\\ value"');
    expect(stringLiteral("line\nbreak")).toBe('"line\\nbreak"');
  });
});

/**
 * Free text from an IR reaches generated source as comments and string literals. It is written
 * by a model reading a user's test case, so it is the least trusted input in the pipeline: these
 * are the escapes that stop it becoming code.
 */
describe("text from the IR cannot become code", () => {
  /** U+2028 ends a `//` comment for a JS parser while looking like a space in an editor. */
  const LINE_SEPARATOR = String.fromCharCode(0x2028);

  it("neutralises a line separator hidden in a description", () => {
    const comment = lineComment(`safe${LINE_SEPARATOR}globalThis.PWNED = true;`);

    expect(comment).not.toContain(LINE_SEPARATOR);
    // The proof, not a proxy for it: a parser sees one comment and no statement.
    expect(() => new Function(comment)).not.toThrow();
    expect(new Function(`${comment}\nreturn 1;`)()).toBe(1);
  });

  it("neutralises a block-comment terminator", () => {
    expect(lineComment("a */ b")).not.toContain("*/");
    expect(docComment("a */ b")).not.toContain("*/");
  });

  it("escapes a line separator inside a string literal", () => {
    const literal = stringLiteral(`a${LINE_SEPARATOR}b`);

    expect(literal).not.toContain(LINE_SEPARATOR);
    expect(new Function(`return ${literal};`)()).toBe(`a${LINE_SEPARATOR}b`);
  });

  it("flattens the model-level description too, not just a step’s", () => {
    const model = login();
    const { files } = generate({
      model: {
        ...model,
        description: "line one\nawait page.evaluate(() => process.exit(1));",
      },
      pages: LOGIN_PAGES,
      options: options(),
    });

    const spec = fileNamed(files, ".spec.ts");
    // One comment line, not a comment followed by a statement.
    expect(spec).toContain("// line one await page.evaluate");
    expect(spec).not.toMatch(/^s*await page.evaluate/m);
  });

  it("carries a hostile description through generation without producing code", () => {
    const model = login();
    const hostile = `open${LINE_SEPARATOR}globalThis.PWNED = true; //`;
    const { files } = generate({
      model: {
        ...model,
        steps: model.steps.map((step, index) =>
          index === 0 ? { ...step, description: hostile } : step,
        ),
      },
      pages: LOGIN_PAGES,
      options: options(),
    });

    const spec = fileNamed(files, ".spec.ts");
    // The raw separator never survives: to a JS parser it ends a line, so a description
    // carrying one could otherwise close a string and start a statement.
    expect(spec).not.toContain(LINE_SEPARATOR);
    expect(spec).toContain("globalThis.PWNED = true;");
    // …and only inside the escaped `test.step` title it became. Containment is stronger here
    // than in the comment this used to be flattened into: a string literal escapes the
    // separator into its six-character form rather than relying on the text having no
    // newline in it.
    expect(spec).toMatch(
      /await test\.step\("open\\u2028globalThis\.PWNED = true; \/\/ \[s1\]", async \(\) => \{/,
    );
  });
});

/**
 * Inputs a locator planner can legitimately produce, and which would otherwise become source
 * code that does not compile. Refusing is the correct answer: the element is real, and only
 * inspection can rename it.
 */
describe("generate refuses input it cannot project correctly", () => {
  it("rejects an element whose name collides with the page object's own members", () => {
    for (const reserved of ["page", "goto"]) {
      expect(() =>
        generate({
          model: login(),
          pages: [
            {
              name: "LoginPage",
              route: "/login",
              elements: [{ name: reserved, strategy: "testId", value: "x" }],
            },
          ],
          options: options(),
        }),
      ).toThrow(new RegExp(`"${reserved}"`));
    }
  });

  it("rejects two page objects with the same name rather than writing one path twice", () => {
    expect(() =>
      generate({
        model: login(),
        pages: [...LOGIN_PAGES, { name: "LoginPage", elements: [] }],
        options: options(),
      }),
    ).toThrow(/both named LoginPage/);
  });

  it("emits one file per path in every golden case", () => {
    const { files } = generate({
      model: irFixture("checkout"),
      pages: CHECKOUT_PAGES,
      flows: [{ name: "login", model: LOGIN_FLOW }],
      options: options(),
    });

    const paths = files.map((file) => file.path);
    expect(new Set(paths).size).toBe(paths.length);
  });
});

/**
 * A page object can exist and still have nothing on it — that is what an uninspected page looks
 * like coming out of the API today. Projecting against it would emit `loginPage.usernameInput`
 * for a getter nobody wrote, so a missing element is reported exactly like a missing page.
 */
describe("an element with no locator is unresolved, not assumed", () => {
  it("reports every element of a page that was listed but never inspected", () => {
    const { unresolved } = generate({
      model: login(),
      pages: [
        { name: "LoginPage", elements: [] },
        { name: "DashboardPage", elements: [] },
      ],
      options: options(),
    });

    expect(unresolved).toEqual([
      { stepId: "s2", page: "LoginPage", element: "usernameInput" },
      { stepId: "s3", page: "LoginPage", element: "passwordInput" },
      { stepId: "s4", page: "LoginPage", element: "submitButton" },
      { stepId: "s5", page: "DashboardPage", element: "heading" },
    ]);
  });

  it("does not report a navigate step, which needs the page and no element", () => {
    const { unresolved } = generate({
      model: login(),
      pages: [
        // Fully inspected but for the dashboard heading.
        LOGIN_PAGES[0]!,
        { name: "DashboardPage", route: "/dashboard", elements: [] },
      ],
      options: options(),
    });

    expect(unresolved).toEqual([{ stepId: "s5", page: "DashboardPage", element: "heading" }]);
  });
});
