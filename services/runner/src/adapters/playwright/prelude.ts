/**
 * Driving a page through an IR, rather than projecting it into a file.
 *
 * The rest of the adapter turns a Test Model into source text a person reviews and commits. This
 * file is the one place that *performs* one instead, and it exists for a single job: putting the
 * application into the state a page needs before inspection can see that page at all.
 *
 * Most often that state is "signed in", but not only — a screen that lists an order needs an
 * order, and a wizard's fourth step needs the first three. So what runs here is a whole test
 * case, not a login routine: whatever a person had to do by hand before the screen appeared, they
 * have already written down as a test case, and this replays it.
 *
 * **This is a second reader of the IR, and that is the cost.** `steps.ts` decides what a step
 * *means* in generated code; this decides what it means live, and the two must not drift.
 * `prelude.test.ts` covers every action against the same fixtures, so a step added to one and not
 * the other fails a test rather than a user's inspection. The alternative — generating a spec and
 * shelling out to the engine — reuses that logic but needs a working copy, an install and a
 * process per inspection, which turns "look at this page" into something slower than the run it
 * is meant to prepare.
 *
 * Nothing here is written to the user's repository. A prelude is scaffolding for one reading.
 */

import {
  isPageTarget,
  isSelectorTarget,
  type Target,
  type TestModel,
  type TestStep,
  type TestValue,
} from "@specra/test-model";

import type { ElementLocator, PageObject } from "../../codegen/types.js";

/**
 * The slice of the engine this file drives.
 *
 * Declared rather than imported for the same reason `inspect.ts` declares its own: this package
 * has no engine dependency and must not gain one. The shapes are the engine's, so a mismatch is a
 * compile error here rather than a failure inside a browser.
 */
export interface PreludePage {
  goto(url: string, options?: { waitUntil?: string; timeout?: number }): Promise<unknown>;
  reload(options?: { timeout?: number }): Promise<unknown>;
  goBack(options?: { timeout?: number }): Promise<unknown>;
  goForward(options?: { timeout?: number }): Promise<unknown>;
  getByTestId(value: string): PreludeLocator;
  getByRole(role: string, options?: { name?: string }): PreludeLocator;
  getByLabel(value: string): PreludeLocator;
  getByPlaceholder(value: string): PreludeLocator;
  getByText(value: string): PreludeLocator;
  getByAltText(value: string): PreludeLocator;
  getByTitle(value: string): PreludeLocator;
  locator(selector: string): PreludeLocator;
  keyboard: { press(key: string): Promise<void> };
}

export interface PreludeLocator {
  click(options?: { button?: "right"; clickCount?: number }): Promise<void>;
  dblclick(): Promise<void>;
  hover(): Promise<void>;
  fill(value: string): Promise<void>;
  clear(): Promise<void>;
  press(key: string): Promise<void>;
  selectOption(value: string): Promise<unknown>;
  check(): Promise<void>;
  uncheck(): Promise<void>;
  setInputFiles(files: string): Promise<void>;
  dragTo(target: PreludeLocator): Promise<void>;
  waitFor(options?: { state?: "visible" | "hidden"; timeout?: number }): Promise<void>;
  first(): PreludeLocator;
}

export interface PreludeContext {
  /** The page objects the prelude's targets resolve through — the same ones codegen is given. */
  pages: PageObject[];
  /**
   * Values for `param` and `secret` references.
   *
   * A secret is referenced by name in the IR and only ever holds a value here, for the length of
   * one inspection. Nothing in this file logs one, and the error a failed step raises names the
   * step and the element, never the value that was typed.
   */
  variables: Record<string, string>;
  /** Flows a `useFlow` step can name, by name. Absent ones are reported, never skipped. */
  flows?: Record<string, TestModel>;
  timeoutMs?: number;
}

/** What a prelude could not do, in words that name the step rather than the engine. */
export class PreludeFailed extends Error {
  readonly stepId: string;

  constructor(stepId: string, message: string) {
    super(message);
    this.name = "PreludeFailed";
    this.stepId = stepId;
  }
}

const DEFAULT_STEP_TIMEOUT_MS = 15_000;

/**
 * Replays a test case against an open page, so a later reading sees the screen behind it.
 *
 * `setup` then `steps`; `teardown` is deliberately not run. Teardown undoes what the case did —
 * signing out, deleting the record it created — which is exactly the state the inspection still
 * needs. A prelude ends where the case's last step leaves the application.
 *
 * Any step that fails stops the prelude. Continuing would inspect a page reached halfway through
 * a sign-in, and a half-finished state that reads as a success is the failure mode this whole
 * feature exists to remove.
 */
export async function runPrelude(
  page: PreludePage,
  model: TestModel,
  context: PreludeContext,
): Promise<void> {
  const steps = [...(model.setup ?? []), ...model.steps];
  for (const step of steps) {
    await runStep(page, step, context, new Set());
  }
}

async function runStep(
  page: PreludePage,
  step: TestStep,
  context: PreludeContext,
  running: Set<string>,
): Promise<void> {
  try {
    await perform(page, step, context, running);
  } catch (error) {
    if (error instanceof PreludeFailed) {
      throw error;
    }
    // The engine's message names a selector and a timeout; the person reading it wrote a step.
    // Both are useful, so the step comes first and the engine's words follow.
    const reason = error instanceof Error ? error.message : String(error);
    throw new PreludeFailed(step.id, `step ${step.id} (${step.action}) failed: ${reason}`);
  }
}

async function perform(
  page: PreludePage,
  step: TestStep,
  context: PreludeContext,
  running: Set<string>,
): Promise<void> {
  const timeout = context.timeoutMs ?? DEFAULT_STEP_TIMEOUT_MS;

  switch (step.action) {
    case "navigate":
      await page.goto(text(step, context), { waitUntil: "domcontentloaded", timeout });
      return;
    case "reload":
      await page.reload({ timeout });
      return;
    case "goBack":
      await page.goBack({ timeout });
      return;
    case "goForward":
      await page.goForward({ timeout });
      return;

    case "fill":
      await locate(page, step, context).fill(text(step, context));
      return;
    case "clear":
      await locate(page, step, context).clear();
      return;
    case "press":
      // A press with no target goes to the page: "press Escape" is about the application, not
      // about one input, and requiring a target would make the common case unwritable.
      if (step.target) {
        await locate(page, step, context).press(text(step, context));
      } else {
        await page.keyboard.press(text(step, context));
      }
      return;
    case "select":
      await locate(page, step, context).selectOption(text(step, context));
      return;
    case "check":
      await locate(page, step, context).check();
      return;
    case "uncheck":
      await locate(page, step, context).uncheck();
      return;
    case "upload":
      await locate(page, step, context).setInputFiles(text(step, context));
      return;

    case "click":
      await locate(page, step, context).click();
      return;
    case "doubleClick":
      await locate(page, step, context).dblclick();
      return;
    case "rightClick":
      await locate(page, step, context).click({ button: "right" });
      return;
    case "hover":
      await locate(page, step, context).hover();
      return;
    case "dragTo": {
      const to = step.to;
      if (!to) {
        throw new PreludeFailed(step.id, `step ${step.id} is a dragTo with no destination`);
      }
      await locate(page, step, context).dragTo(resolve(page, to, step, context));
      return;
    }

    case "waitFor":
      await locate(page, step, context).waitFor({ state: "visible", timeout });
      return;

    /**
     * An assertion in a prelude is a checkpoint, not a verdict.
     *
     * The case being replayed asserts things — "the dashboard greets me by name" — and those
     * assertions are what tell us the prelude actually worked. Waiting for the element is the
     * part that matters here: it is what makes a slow sign-in finish before the next step. The
     * *result* of the assertion is not this feature's business; a prelude that reaches the right
     * screen has done its job even if the greeting has the wrong name, and reporting that as an
     * inspection failure would blame the wrong thing.
     */
    case "assert":
      if (step.target) {
        await locate(page, step, context).waitFor({ state: "visible", timeout });
      }
      return;

    case "useFlow": {
      const name = step.flow;
      if (!name) {
        throw new PreludeFailed(step.id, `step ${step.id} is a useFlow with no flow named`);
      }
      const flow = context.flows?.[name];
      if (!flow) {
        // Named but absent. Skipping would run a prelude missing the half that signs in, and
        // the inspection after it would look like an ordinary redirect — a wrong answer for a
        // knowable reason.
        throw new PreludeFailed(step.id, `step ${step.id} names a flow that was not provided: ${name}`);
      }
      // A flow that reaches itself would recurse until the stack gave out, and the message would
      // name neither flow. The set is per top-level step, so two siblings may use the same flow.
      if (running.has(name)) {
        throw new PreludeFailed(step.id, `flow ${name} uses itself, directly or through another`);
      }
      running.add(name);
      try {
        for (const inner of [...(flow.setup ?? []), ...flow.steps]) {
          await runStep(page, inner, context, running);
        }
      } finally {
        running.delete(name);
      }
      return;
    }

    default: {
      // Exhaustive by construction: an action added to the schema without a case here is a
      // compile error, not an inspection that fails in front of a user.
      const exhaustive: never = step.action;
      throw new PreludeFailed(step.id, `unsupported action ${String(exhaustive)}`);
    }
  }
}

/** The step's own target, resolved. Steps that need one and have none say so by name. */
function locate(page: PreludePage, step: TestStep, context: PreludeContext): PreludeLocator {
  if (!step.target) {
    throw new PreludeFailed(step.id, `step ${step.id} (${step.action}) has no target`);
  }
  return resolve(page, step.target, step, context);
}

/**
 * A target becomes a locator.
 *
 * A page target is resolved through the page objects, which is invariant 5 holding here too: the
 * locator was read off a real page, and this file never invents one. A selector target is the
 * IR's escape hatch and passes through as written.
 *
 * `.first()` on the result, deliberately. A prelude is not a test — nobody is asserting that the
 * application has exactly one Continue button — and failing an inspection over a strict-mode
 * violation would block the reading for a reason the person cannot act on from here.
 */
function resolve(
  page: PreludePage,
  target: Target,
  step: TestStep,
  context: PreludeContext,
): PreludeLocator {
  if (isSelectorTarget(target)) {
    const { strategy, value } = target.selector;
    return (strategy === "xpath" ? page.locator(`xpath=${value}`) : page.locator(value)).first();
  }

  if (!isPageTarget(target)) {
    throw new PreludeFailed(step.id, `step ${step.id} has a target of no known kind`);
  }

  const object = context.pages.find((candidate) => candidate.name === target.page);
  if (!object) {
    throw new PreludeFailed(
      step.id,
      `step ${step.id} targets ${target.page}, which has not been inspected`,
    );
  }
  if (!target.element) {
    throw new PreludeFailed(step.id, `step ${step.id} targets a page with no element`);
  }
  const element = object.elements.find((candidate) => candidate.name === target.element);
  if (!element) {
    throw new PreludeFailed(
      step.id,
      `step ${step.id} targets ${target.page}.${target.element}, which that page object does not have`,
    );
  }
  return byStrategy(page, element).first();
}

/** The mapping the rest of the adapter writes as source text, performed instead. */
function byStrategy(page: PreludePage, element: ElementLocator): PreludeLocator {
  switch (element.strategy) {
    case "testId":
      return page.getByTestId(element.value);
    case "role":
      return element.name_
        ? page.getByRole(element.value, { name: element.name_ })
        : page.getByRole(element.value);
    case "label":
      return page.getByLabel(element.value);
    case "placeholder":
      return page.getByPlaceholder(element.value);
    case "text":
      return page.getByText(element.value);
    case "altText":
      return page.getByAltText(element.value);
    case "title":
      return page.getByTitle(element.value);
    case "xpath":
      return page.locator(`xpath=${element.value}`);
    case "css":
      return page.locator(element.value);
  }
}

/**
 * The text a step types, navigates to, or presses.
 *
 * A `secret` resolves out of the variables the API decrypted at dispatch and is never held
 * anywhere else — not in a message, not in a thrown error. A missing one fails the step by
 * *name*, which is the one thing about a secret that is safe to say out loud.
 */
function text(step: TestStep, context: PreludeContext): string {
  const value: TestValue | undefined = step.value;
  if (!value) {
    throw new PreludeFailed(step.id, `step ${step.id} (${step.action}) has no value`);
  }
  switch (value.kind) {
    case "literal":
      return String(value.value);
    case "param":
    case "secret": {
      const resolved = context.variables[value.name];
      if (resolved === undefined) {
        throw new PreludeFailed(
          step.id,
          `step ${step.id} needs ${value.name}, which this environment does not define`,
        );
      }
      return resolved;
    }
  }
}
