/**
 * One IR step → one or more lines of Playwright.
 *
 * The switch over `StepAction` is exhaustive by construction: the return type is `string[]` and
 * the default branch asserts `never`, so adding an action to the schema without adding it here
 * is a compile error rather than a generation that fails at runtime.
 *
 * Nothing here waits by time. `waitFor` projects to an assertion with a condition, which is what
 * the IR means by waiting — a duration in an IR is a flake with a schema.
 */

import {
  isSelectorTarget,
  type Target,
  type TestStep,
  type TestValue,
} from "@specra/test-model";

import {
  lineComment,
  pageVariable,
  scalarLiteral,
  stringLiteral,
} from "../../codegen/naming.js";
import { rawSelectorExpression } from "./locators.js";

/** One level in, relative to wherever the caller places the `test.step` wrapper. */
const STEP_INDENT = "  ";

/** How a step reaches its element, given what the page objects resolved. */
export interface StepContext {
  /**
   * The elements each known page actually has, by page name.
   *
   * The elements matter as much as the page: inspection can produce a page object with nothing
   * on it yet, and projecting `loginPage.usernameInput` against that is a member access to a
   * getter nobody wrote. A missing page and a missing element are the same failure — a locator
   * that does not exist — so they are reported the same way.
   */
  knownPages: Map<string, Set<string>>;
  /** Set when the step's element is missing from its page object. */
  onUnresolved: (step: TestStep, page: string, element?: string) => void;
}

/**
 * One IR step, wrapped in `test.step` so the step survives into the result.
 *
 * The wrapper is what makes a failure locatable. Playwright's report records which step carried
 * the error but never mentions it in the message, so the IR step id has to be *in the title* —
 * that is the only channel from a generated line back to the manual step a person wrote, and it
 * is what lets a run detail highlight both at once. It also gives the trace viewer a readable
 * outline instead of a flat list of clicks.
 *
 * The title is the step's own description when it has one, so a reader of the trace sees the
 * sentence from the test case rather than a restatement of the code below it.
 */
export function renderStep(step: TestStep, context: StepContext): string[] {
  const body = renderAction(step, context);
  const title = `${step.description?.trim() || describe(step)} [${step.id}]`;

  const lines: string[] = [];
  lines.push(`await test.step(${stringLiteral(title)}, async () => {`);
  for (const line of body) {
    lines.push(`${STEP_INDENT}${line}`);
  }
  lines.push("});");
  return lines;
}

/** A short label for a step the author gave no description, so the trace is never blank. */
function describe(step: TestStep): string {
  const target = step.target;
  if (target && typeof target === "object" && "page" in target) {
    const page = (target as { page?: string }).page;
    const element = (target as { element?: string }).element;
    return element ? `${step.action} ${page}.${element}` : `${step.action} ${page}`;
  }
  return step.action;
}

function renderAction(step: TestStep, context: StepContext): string[] {
  switch (step.action) {
    case "navigate":
      return [`await ${target(step, step.target, context)}.goto();`];
    case "reload":
      return ["await page.reload();"];
    case "goBack":
      return ["await page.goBack();"];
    case "goForward":
      return ["await page.goForward();"];
    case "fill":
      return [`await ${target(step, step.target, context)}.fill(${value(step.value)});`];
    case "clear":
      return [`await ${target(step, step.target, context)}.clear();`];
    case "press":
      return [`await ${target(step, step.target, context)}.press(${value(step.value)});`];
    case "select":
      return [
        `await ${target(step, step.target, context)}.selectOption(${value(step.value)});`,
      ];
    case "check":
      return [`await ${target(step, step.target, context)}.check();`];
    case "uncheck":
      return [`await ${target(step, step.target, context)}.uncheck();`];
    case "upload":
      return [
        `await ${target(step, step.target, context)}.setInputFiles(${value(step.value)});`,
      ];
    case "click":
      return [`await ${target(step, step.target, context)}.click();`];
    case "doubleClick":
      return [`await ${target(step, step.target, context)}.dblclick();`];
    case "rightClick":
      return [`await ${target(step, step.target, context)}.click({ button: "right" });`];
    case "hover":
      return [`await ${target(step, step.target, context)}.hover();`];
    case "dragTo":
      return [
        `await ${target(step, step.target, context)}.dragTo(${target(step, step.to, context)});`,
      ];
    case "waitFor":
    case "assert":
      return [assertion(step, context)];
    case "useFlow":
      return [`await ${step.flow}Flow(page);`];
    default: {
      const exhaustive: never = step.action;
      throw new Error(`No Playwright projection for action ${String(exhaustive)}`);
    }
  }
}

/**
 * `assert` and `waitFor` project to the same expression: Playwright's web-first assertions
 * already retry until the condition holds or the test times out, so "wait until visible" and
 * "check it is visible" are one call. The difference lives in the IR, for a human to read.
 */
function assertion(step: TestStep, context: StepContext): string {
  const condition = step.assertion?.condition;
  if (!condition) {
    throw new Error(`Step ${step.id} is an ${step.action} with no assertion`);
  }
  const expected = step.assertion?.expected;
  const subject = target(step, step.target, context);

  switch (condition) {
    case "visible":
      return `await expect(${subject}).toBeVisible();`;
    case "hidden":
      return `await expect(${subject}).toBeHidden();`;
    case "enabled":
      return `await expect(${subject}).toBeEnabled();`;
    case "disabled":
      return `await expect(${subject}).toBeDisabled();`;
    case "checked":
      return `await expect(${subject}).toBeChecked();`;
    case "textEquals":
      return `await expect(${subject}).toHaveText(${scalarLiteral(require(expected, step))});`;
    case "textContains":
      return `await expect(${subject}).toContainText(${scalarLiteral(require(expected, step))});`;
    case "valueEquals":
      return `await expect(${subject}).toHaveValue(${scalarLiteral(require(expected, step))});`;
    case "countEquals":
      return `await expect(${subject}).toHaveCount(${scalarLiteral(require(expected, step))});`;
    // The one condition about the page rather than an element: the subject is the URL itself,
    // and the IR's expectation is a pattern, so it projects to a RegExp and not a string.
    case "urlMatches":
      return `await expect(page).toHaveURL(new RegExp(${stringLiteral(String(require(expected, step)))}));`;
    case "attributeEquals":
      return `await expect(${subject}).toHaveAttribute(${stringLiteral(
        step.assertion?.attribute ?? "",
      )}, ${scalarLiteral(require(expected, step))});`;
    default: {
      const exhaustive: never = condition;
      throw new Error(`No Playwright projection for condition ${String(exhaustive)}`);
    }
  }
}

function require<T>(expected: T | undefined, step: TestStep): T {
  if (expected === undefined) {
    throw new Error(`Step ${step.id} needs an expected value for ${step.assertion?.condition}`);
  }
  return expected;
}

/**
 * A page target becomes a page-object member; the escape hatch becomes a raw locator, inline,
 * where a reviewer can see the debt.
 */
function target(step: TestStep, subject: Target | undefined, context: StepContext): string {
  if (!subject) {
    throw new Error(`Step ${step.id} has no target`);
  }
  if (isSelectorTarget(subject)) {
    return rawSelectorExpression(subject.selector.strategy, subject.selector.value, "page");
  }
  const elements = context.knownPages.get(subject.page);
  if (
    elements === undefined ||
    (subject.element !== undefined && !elements.has(subject.element))
  ) {
    context.onUnresolved(step, subject.page, subject.element);
  }
  const variable = pageVariable(subject.page);
  return subject.element ? `${variable}.${subject.element}` : variable;
}

function value(subject: TestValue | undefined): string {
  if (!subject) {
    throw new Error("This action carries a value and none was given");
  }
  switch (subject.kind) {
    case "literal":
      return scalarLiteral(subject.value);
    // A parameter is read at run time from the environment the runner was dispatched with; a
    // secret is read the same way and never written into the file. Both are names here.
    case "param":
    case "secret":
      return `${subject.name}()`;
    default: {
      const exhaustive: never = subject;
      throw new Error(`Unknown value kind ${String(exhaustive)}`);
    }
  }
}
