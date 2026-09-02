/**
 * A reusable flow, projected once.
 *
 * `useFlow` exists so "log in first" is one file rather than the first four steps of forty
 * specs. The projection is the spec's, minus the `test()` wrapper: a plain async function that
 * takes the page.
 */

import { isPageTarget, type TestModel, type TestStep } from "@specra/test-model";

import { docComment, kebabCase, pageVariable, upperFirst } from "../../codegen/naming.js";
import { renderStep, type StepContext } from "./steps.js";

const INDENT = "  ";

export interface FlowRender {
  contents: string;
  unresolved: { stepId: string; page: string; element?: string }[];
}

export function renderFlow(
  name: string,
  model: TestModel,
  knownPages: Map<string, Set<string>>,
): FlowRender {
  const unresolved: FlowRender["unresolved"] = [];
  const context: StepContext = {
    knownPages,
    onUnresolved: (step, page, element) => unresolved.push({ stepId: step.id, page, element }),
  };
  const steps = [...(model.setup ?? []), ...model.steps, ...(model.teardown ?? [])];

  // Every page this flow targets, inspected or not. Filtering by `knownPages` here would emit a
  // reference to a page object the file never constructs — code that does not compile. A page
  // nobody has inspected is reported through `unresolved` instead, exactly as in a spec.
  const used: string[] = [];
  for (const step of steps) {
    for (const target of [step.target, step.to]) {
      if (target && isPageTarget(target) && !used.includes(target.page)) {
        used.push(target.page);
      }
    }
  }

  // `expect` only when something is asserted: an unused import is a lint error in a strict
  // project, and the generated project is the user's to lint however they like.
  const asserts = steps.some((step) => step.action === "assert" || step.action === "waitFor");

  const lines: string[] = [];
  lines.push(
    asserts
      ? 'import { expect, type Page } from "@playwright/test";'
      : 'import type { Page } from "@playwright/test";',
  );
  lines.push("");
  for (const page of used) {
    lines.push(`import { ${page} } from "../pages/${page}.js";`);
  }
  const parameters = model.parameters ?? [];
  if (parameters.length > 0) {
    lines.push(
      `import { ${parameters.map((p) => p.name).join(", ")} } from "../fixtures/environment.js";`,
    );
  }
  lines.push("");
  lines.push(
    `/** ${docComment(model.description ?? `The ${name} flow, shared by every test that needs it.`)} */`,
  );
  lines.push(`export async function ${name}Flow(page: Page): Promise<void> {`);
  for (const page of used) {
    lines.push(`${INDENT}const ${pageVariable(page)} = new ${page}(page);`);
  }
  if (used.length > 0) {
    lines.push("");
  }
  for (const step of steps) {
    for (const line of renderStep(step, context)) {
      lines.push(`${INDENT}${line}`);
    }
  }
  lines.push("}");
  lines.push("");
  return { contents: lines.join("\n"), unresolved };
}

/** `login` → `flows/login.flow.ts`, and the exported symbol is `loginFlow`. */
export function flowFileName(name: string): string {
  return `${kebabCase(name)}.flow.ts`;
}

export function flowExportName(name: string): string {
  return `${name}Flow`;
}

/** Unused today; kept beside its siblings so a caller never has to guess the casing rule. */
export function flowClassName(name: string): string {
  return upperFirst(name);
}
