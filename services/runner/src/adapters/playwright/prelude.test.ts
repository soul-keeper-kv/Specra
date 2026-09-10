import { describe, expect, it } from "vitest";

import type { TestModel, TestStep } from "@specra/test-model";

import { PreludeFailed, runPrelude, type PreludeLocator, type PreludePage } from "./prelude.js";
import type { PageObject } from "../../codegen/types.js";

/**
 * Replaying a test case to reach the screen behind it, without a browser.
 *
 * The page is recorded rather than driven: every call lands in a list, so a test asserts what the
 * engine was asked to do. That is the whole contract of this file — it decides what an IR step
 * *means* live, and `steps.ts` decides what the same step means in generated code. Two readers of
 * one schema drift unless both are pinned, so every action appears here.
 */

type Call = { method: string; args: unknown[] };

function recorder() {
  const calls: Call[] = [];
  const record = (method: string, ...args: unknown[]) => {
    calls.push({ method, args });
  };

  const locator = (label: string): PreludeLocator => {
    const self: PreludeLocator = {
      click: async (options) => record(`${label}.click`, options),
      dblclick: async () => record(`${label}.dblclick`),
      hover: async () => record(`${label}.hover`),
      fill: async (value) => record(`${label}.fill`, value),
      clear: async () => record(`${label}.clear`),
      press: async (key) => record(`${label}.press`, key),
      selectOption: async (value) => record(`${label}.selectOption`, value),
      check: async () => record(`${label}.check`),
      uncheck: async () => record(`${label}.uncheck`),
      setInputFiles: async (files) => record(`${label}.setInputFiles`, files),
      dragTo: async (target) => record(`${label}.dragTo`, target),
      waitFor: async (options) => record(`${label}.waitFor`, options?.state),
      first: () => self,
    };
    return self;
  };

  const page: PreludePage = {
    goto: async (url) => record("goto", url),
    reload: async () => record("reload"),
    goBack: async () => record("goBack"),
    goForward: async () => record("goForward"),
    getByTestId: (value) => locator(`testId(${value})`),
    getByRole: (role, options) =>
      locator(options?.name ? `role(${role}, ${options.name})` : `role(${role})`),
    getByLabel: (value) => locator(`label(${value})`),
    getByPlaceholder: (value) => locator(`placeholder(${value})`),
    getByText: (value) => locator(`text(${value})`),
    getByAltText: (value) => locator(`altText(${value})`),
    getByTitle: (value) => locator(`title(${value})`),
    locator: (selector) => locator(`selector(${selector})`),
    keyboard: { press: async (key) => record("keyboard.press", key) },
  };

  return { page, calls, methods: () => calls.map((call) => call.method) };
}

const LOGIN_PAGE: PageObject = {
  name: "LoginPage",
  elements: [
    { name: "emailInput", strategy: "testId", value: "email" },
    { name: "passwordInput", strategy: "label", value: "Password" },
    { name: "submitButton", strategy: "role", value: "button", name_: "Log in" },
  ],
};

function step(overrides: Partial<TestStep> & Pick<TestStep, "action">): TestStep {
  return { id: "s1", derived: true, ...overrides };
}

function model(...steps: TestStep[]): TestModel {
  return { irVersion: 1, name: "Sign in", steps };
}

async function run(steps: TestStep[], context: Partial<Parameters<typeof runPrelude>[2]> = {}) {
  const { page, calls, methods } = recorder();
  await runPrelude(page, model(...steps), {
    pages: [LOGIN_PAGE],
    variables: {},
    ...context,
  });
  return { calls, methods: methods() };
}

describe("replaying a sign-in", () => {
  it("drives the whole case in order", async () => {
    const { methods } = await run(
      [
        step({ id: "s1", action: "navigate", value: { kind: "literal", value: "/login" } }),
        step({
          id: "s2",
          action: "fill",
          target: { page: "LoginPage", element: "emailInput" },
          value: { kind: "param", name: "QA_USER" },
        }),
        step({
          id: "s3",
          action: "fill",
          target: { page: "LoginPage", element: "passwordInput" },
          value: { kind: "secret", name: "QA_PASSWORD" },
        }),
        step({ id: "s4", action: "click", target: { page: "LoginPage", element: "submitButton" } }),
      ],
      { variables: { QA_USER: "qa@acme.dev", QA_PASSWORD: "hunter2" } },
    );

    expect(methods).toEqual([
      "goto",
      "testId(email).fill",
      "label(Password).fill",
      "role(button, Log in).click",
    ]);
  });

  /** Invariant 6: a secret is a name in the IR and a value only for the length of one job. */
  it("types a secret without it appearing anywhere but the field", async () => {
    const { calls } = await run(
      [
        step({
          action: "fill",
          target: { page: "LoginPage", element: "passwordInput" },
          value: { kind: "secret", name: "QA_PASSWORD" },
        }),
      ],
      { variables: { QA_PASSWORD: "hunter2" } },
    );

    expect(calls[0]?.args).toEqual(["hunter2"]);
  });

  /**
   * The message says which variable is missing, never what it should have held. A secret's name
   * is the one thing about it that is safe to say out loud.
   */
  it("names the missing variable rather than the value", async () => {
    await expect(
      run([
        step({
          action: "fill",
          target: { page: "LoginPage", element: "passwordInput" },
          value: { kind: "secret", name: "QA_PASSWORD" },
        }),
      ]),
    ).rejects.toThrow(/QA_PASSWORD/);
  });
});

describe("resolving a target", () => {
  /** Invariant 5 holds here too: the locator was read off a real page, never invented. */
  it("resolves each strategy the way the generated code would", async () => {
    const pages: PageObject[] = [
      {
        name: "P",
        elements: [
          { name: "byTestId", strategy: "testId", value: "t" },
          { name: "byRole", strategy: "role", value: "button", name_: "Go" },
          { name: "byLabel", strategy: "label", value: "L" },
          { name: "byPlaceholder", strategy: "placeholder", value: "P" },
          { name: "byText", strategy: "text", value: "T" },
          { name: "byAltText", strategy: "altText", value: "A" },
          { name: "byTitle", strategy: "title", value: "Ti" },
          { name: "byCss", strategy: "css", value: "#c" },
          { name: "byXpath", strategy: "xpath", value: "//x" },
        ],
      },
    ];
    const names = pages[0]!.elements.map((element) => element.name);
    const { methods } = await run(
      names.map((element, index) =>
        step({ id: `s${index}`, action: "click", target: { page: "P", element } }),
      ),
      { pages },
    );

    expect(methods).toEqual([
      "testId(t).click",
      "role(button, Go).click",
      "label(L).click",
      "placeholder(P).click",
      "text(T).click",
      "altText(A).click",
      "title(Ti).click",
      "selector(#c).click",
      "selector(xpath=//x).click",
    ]);
  });

  it("passes the IR's escape hatch through as written", async () => {
    const { methods } = await run([
      step({ action: "click", target: { selector: { strategy: "css", value: ".btn" } } }),
    ]);

    expect(methods).toEqual(["selector(.btn).click"]);
  });

  /**
   * A page nobody inspected has no locators, and inventing one is the thing this product refuses
   * to do. Saying which page is missing is what makes it fixable.
   */
  it("refuses a page that was never inspected", async () => {
    await expect(
      run([step({ action: "click", target: { page: "Unknown", element: "x" } })]),
    ).rejects.toThrow(/Unknown.*not been inspected/);
  });

  it("refuses an element the page object does not have", async () => {
    await expect(
      run([step({ action: "click", target: { page: "LoginPage", element: "ghostButton" } })]),
    ).rejects.toThrow(/LoginPage\.ghostButton/);
  });
});

describe("every action the schema allows", () => {
  const target = { page: "LoginPage", element: "emailInput" } as const;
  const literal = { kind: "literal", value: "x" } as const;

  it.each([
    ["reload", step({ action: "reload" }), "reload"],
    ["goBack", step({ action: "goBack" }), "goBack"],
    ["goForward", step({ action: "goForward" }), "goForward"],
    ["clear", step({ action: "clear", target }), "testId(email).clear"],
    ["select", step({ action: "select", target, value: literal }), "testId(email).selectOption"],
    ["check", step({ action: "check", target }), "testId(email).check"],
    ["uncheck", step({ action: "uncheck", target }), "testId(email).uncheck"],
    ["upload", step({ action: "upload", target, value: literal }), "testId(email).setInputFiles"],
    ["doubleClick", step({ action: "doubleClick", target }), "testId(email).dblclick"],
    ["rightClick", step({ action: "rightClick", target }), "testId(email).click"],
    ["hover", step({ action: "hover", target }), "testId(email).hover"],
    ["waitFor", step({ action: "waitFor", target }), "testId(email).waitFor"],
  ])("performs %s", async (_name, given, expected) => {
    const { methods } = await run([given]);
    expect(methods).toEqual([expected]);
  });

  /** A right click is a click with a button, and the option is the whole difference. */
  it("sends the right button for a right click", async () => {
    const { calls } = await run([step({ action: "rightClick", target })]);
    expect(calls[0]?.args[0]).toEqual({ button: "right" });
  });

  /**
   * "Press Escape" is about the application, not about one input. Requiring a target would make
   * the ordinary case unwritable.
   */
  it("presses on the page when a press has no target", async () => {
    const { methods } = await run([
      step({ action: "press", value: { kind: "literal", value: "Escape" } }),
    ]);
    expect(methods).toEqual(["keyboard.press"]);
  });

  it("presses on the element when a press has one", async () => {
    const { methods } = await run([
      step({ action: "press", target, value: { kind: "literal", value: "Enter" } }),
    ]);
    expect(methods).toEqual(["testId(email).press"]);
  });

  it("drags between two targets", async () => {
    const { methods } = await run([
      step({
        action: "dragTo",
        target,
        to: { page: "LoginPage", element: "submitButton" },
      }),
    ]);
    expect(methods).toEqual(["testId(email).dragTo"]);
  });

  /**
   * An assertion in a prelude is a checkpoint, not a verdict: waiting is what makes a slow
   * sign-in finish, and whether the greeting has the right name is not this feature's business.
   */
  it("waits for an assertion's target rather than judging it", async () => {
    const { calls } = await run([
      step({ action: "assert", target, assertion: { condition: "visible" } }),
    ]);
    expect(calls[0]?.method).toBe("testId(email).waitFor");
    expect(calls[0]?.args).toEqual(["visible"]);
  });

  it("skips an assertion with no target instead of failing", async () => {
    const { methods } = await run([
      step({ action: "assert", assertion: { condition: "visible" } }),
    ]);
    expect(methods).toEqual([]);
  });
});

describe("composing flows", () => {
  it("runs the steps of a flow it was given", async () => {
    const { methods } = await run([step({ action: "useFlow", flow: "login" })], {
      flows: {
        login: model(
          step({ action: "click", target: { page: "LoginPage", element: "submitButton" } }),
        ),
      },
    });

    expect(methods).toEqual(["role(button, Log in).click"]);
  });

  /**
   * Skipping a missing flow would run a prelude without the half that signs in, and the reading
   * afterwards would look like an ordinary redirect — a wrong answer for a knowable reason.
   */
  it("refuses a flow it was not given", async () => {
    await expect(run([step({ action: "useFlow", flow: "login" })])).rejects.toThrow(
      /names a flow that was not provided: login/,
    );
  });

  it("refuses a flow that uses itself", async () => {
    await expect(
      run([step({ action: "useFlow", flow: "loop" })], {
        flows: { loop: model(step({ action: "useFlow", flow: "loop" })) },
      }),
    ).rejects.toThrow(/uses itself/);
  });
});

describe("stopping on a failure", () => {
  /**
   * Continuing would inspect a page reached halfway through a sign-in, and a half-finished state
   * that reads as a success is the failure this whole feature exists to remove.
   */
  it("stops at the first failing step", async () => {
    const { page, methods } = recorder();
    const failing = model(
      step({ id: "s1", action: "click", target: { page: "Missing", element: "x" } }),
      step({ id: "s2", action: "click", target: { page: "LoginPage", element: "submitButton" } }),
    );

    await expect(
      runPrelude(page, failing, { pages: [LOGIN_PAGE], variables: {} }),
    ).rejects.toBeInstanceOf(PreludeFailed);
    expect(methods()).toEqual([]);
  });

  /** The person reading the message wrote a step, so the step is what the message names. */
  it("names the step that failed", async () => {
    const { page } = recorder();
    const failing = model(step({ id: "s7", action: "fill", target: undefined }));

    await expect(
      runPrelude(page, failing, { pages: [LOGIN_PAGE], variables: {} }),
    ).rejects.toMatchObject({ stepId: "s7", message: expect.stringContaining("s7") });
  });
});

describe("what a prelude leaves behind", () => {
  /**
   * Teardown undoes what the case did — signing out, deleting the record it created — which is
   * exactly the state the inspection still needs. A prelude ends where the last step leaves it.
   */
  it("runs setup and steps but never teardown", async () => {
    const { page, methods } = recorder();
    await runPrelude(
      page,
      {
        irVersion: 1,
        name: "Sign in",
        setup: [step({ id: "a", action: "navigate", value: { kind: "literal", value: "/" } })],
        steps: [step({ id: "b", action: "click", target: { page: "LoginPage", element: "submitButton" } })],
        teardown: [step({ id: "c", action: "click", target: { page: "LoginPage", element: "emailInput" } })],
      },
      { pages: [LOGIN_PAGE], variables: {} },
    );

    expect(methods()).toEqual(["goto", "role(button, Log in).click"]);
  });
});
