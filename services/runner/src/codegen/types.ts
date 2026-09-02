/**
 * The codegen job's contract: what `apps/api` sends, and what comes back.
 *
 * Engine-free on purpose: an `ElementLocator` says `testId` or `role`, never the call an engine
 * would make of it. That projection happens in `../adapters/playwright/`, and nowhere else.
 */

import type { TestModel } from "@specra/test-model";

/** How an element is found, in the order the planner prefers (02-test-model-ir.md). */
export const LOCATOR_STRATEGIES = [
  "testId",
  "role",
  "label",
  "placeholder",
  "text",
  "altText",
  "title",
  "css",
  "xpath",
] as const;

export type LocatorStrategy = (typeof LOCATOR_STRATEGIES)[number];

/**
 * One inspected element of a page object.
 *
 * `name` is the identifier an IR target uses; `fallback` is the runner-up the planner scored,
 * which failure analysis proposes first when the primary drifts.
 */
export interface ElementLocator {
  name: string;
  strategy: LocatorStrategy;
  value: string;
  /** Disambiguates a strategy that matches several elements — `role` with an accessible name. */
  name_?: string;
  fallback?: { strategy: LocatorStrategy; value: string };
  /** 0–1, as the planner scored it. Carried through to the page object as a comment. */
  confidence?: number;
}

/** A known page of the application under test, with the locators inspection resolved. */
export interface PageObject {
  name: string;
  route?: string;
  elements: ElementLocator[];
}

/** A reusable flow, already projected once and referenced by `useFlow` steps. */
export interface FlowDefinition {
  name: string;
  model: TestModel;
}

/** Everything about the target project that is a choice rather than a fact about the IR. */
export interface ProjectOptions {
  /** The TC-n reference; names the spec file and the committed IR. */
  reference: string;
  /** Sub-folder of `tests/`, from the case's first tag: `tests/auth/login.spec.ts`. */
  area?: string;
  /** Written into `.specra/project.json` so the repository says which adapter wrote it. */
  adapterVersion: string;
  /** Only emitted when the project is being scaffolded for the first time. */
  scaffold?: boolean;
  /** Package name for a scaffolded project's package.json. */
  projectName?: string;
  /** Browsers the scaffolded config declares as projects. */
  browsers?: string[];
}

export interface CodegenRequest {
  model: TestModel;
  pages: PageObject[];
  flows?: FlowDefinition[];
  options: ProjectOptions;
}

export type GeneratedFileRole = "SPEC" | "PAGE" | "FIXTURE" | "CONFIG" | "MODEL";

export interface GeneratedFile {
  path: string;
  contents: string;
  role: GeneratedFileRole;
}

export interface CodegenResult {
  files: GeneratedFile[];
  /** Every page.element an IR step named that no page object supplies. */
  unresolved: { stepId: string; page: string; element?: string }[];
}
