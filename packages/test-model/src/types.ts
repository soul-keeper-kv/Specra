/**
 * TypeScript mirror of `schema/test-model.v1.schema.json`.
 *
 * The schema is the source of truth. These types exist so editors and the runner can work
 * with an IR without re-deriving its shape — and `schema-parity.test.ts` asserts that the
 * closed vocabularies below are exactly the enums the schema declares, so the two cannot
 * drift apart silently.
 */

/** Every action the IR can express. Closed on purpose: see the `specra-testmodel` skill. */
export const ACTIONS = [
  "navigate",
  "reload",
  "goBack",
  "goForward",
  "fill",
  "clear",
  "press",
  "select",
  "check",
  "uncheck",
  "upload",
  "click",
  "doubleClick",
  "rightClick",
  "hover",
  "dragTo",
  "waitFor",
  "assert",
  "useFlow",
] as const;

export type StepAction = (typeof ACTIONS)[number];

/** Conditions an `assert` states now, and a `waitFor` waits for. */
export const CONDITIONS = [
  "visible",
  "hidden",
  "enabled",
  "disabled",
  "checked",
  "textEquals",
  "textContains",
  "valueEquals",
  "countEquals",
  "urlMatches",
  "attributeEquals",
] as const;

export type AssertCondition = (typeof CONDITIONS)[number];

export const VALUE_KINDS = ["literal", "param", "secret"] as const;

export type ValueKind = (typeof VALUE_KINDS)[number];

export const PARAMETER_TYPES = ["string", "number", "boolean"] as const;

export type ParameterType = (typeof PARAMETER_TYPES)[number];

/** Strategies the escape hatch allows. The planner's own ranking lives on the page object. */
export const SELECTOR_STRATEGIES = ["css", "xpath"] as const;

export type SelectorStrategy = (typeof SELECTOR_STRATEGIES)[number];

export interface Parameter {
  name: string;
  type: ParameterType;
  required?: boolean;
  /** Supplied by an Environment at run time. Its value never enters this document. */
  secret?: boolean;
  description?: string;
}

export type TestValue =
  | { kind: "literal"; value: string | number | boolean }
  | { kind: "param"; name: string }
  | { kind: "secret"; name: string };

/** A reference into the project's page objects — never a locator. */
export interface PageTarget {
  page: string;
  element?: string;
}

/** The escape hatch. Recorded as debt on the automation test; never the first choice. */
export interface SelectorTarget {
  selector: { strategy: SelectorStrategy; value: string };
}

export type Target = PageTarget | SelectorTarget;

export interface Assertion {
  condition: AssertCondition;
  expected?: string | number | boolean;
  attribute?: string;
}

export interface TestStep {
  id: string;
  /** The manual steps this came from. Empty means the step must declare itself `derived`. */
  sourceStepIds?: string[];
  derived?: boolean;
  description?: string;
  action: StepAction;
  target?: Target;
  /** Only `dragTo` has a second target. */
  to?: Target;
  value?: TestValue;
  assertion?: Assertion;
  /** Only `useFlow` names a flow. */
  flow?: string;
}

export interface TestModel {
  irVersion: 1;
  name: string;
  description?: string;
  tags?: string[];
  parameters?: Parameter[];
  setup?: TestStep[];
  steps: TestStep[];
  teardown?: TestStep[];
}

export function isSelectorTarget(target: Target): target is SelectorTarget {
  return "selector" in target;
}

export function isPageTarget(target: Target): target is PageTarget {
  return "page" in target;
}
