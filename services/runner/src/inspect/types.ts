/**
 * The `inspect` job's contract: what `apps/api` sends, and what comes back.
 *
 * Engine-free like the other two. A caller says "look at this URL and tell me what is on it";
 * nothing here names a browser API, and the reply is a list of candidate locators with scores —
 * facts about the page, not instructions for any particular tool.
 *
 * This job exists because of invariant 5: a model that invents `#login-btn` is the largest source
 * of flake in tools like this, and inspection is cheap. Everything below is arranged so the
 * locator that ends up in a page object was *read* rather than guessed.
 */

/** The ranked strategies of 02-test-model-ir.md, best first. Order is load-bearing. */
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

export interface InspectRequest {
  /** Absolute URL to open. The API joins the environment's base URL to the route. */
  url: string;
  /**
   * The working copy, which is where the browser engine is resolved from.
   *
   * This package has no engine dependency of its own — the same containment rule execution
   * follows — so inspection borrows the one the user's project already installed.
   */
  projectDir: string;
  /**
   * What the page should be called. The IR references pages by name, so the caller decides it —
   * inspection has no idea that `/login` is `LoginPage`.
   */
  pageName: string;
  /**
   * Injected into the browser context as cookies/storage would be — not used yet, reserved so a
   * page behind a login can be inspected without this contract changing. Secret values are
   * masked out of everything this job returns, exactly as in `execute`.
   */
  variables?: Record<string, string>;
  /** The attribute the project treats as a test id. Configurable because teams differ. */
  testIdAttribute?: string;
  timeoutMs?: number;
  /** Forces the direct path; a container is used otherwise. The runner's own tests set it. */
  isolation?: "container" | "process";
}

/**
 * One way of addressing an element, with the planner's reading of how good it is.
 *
 * Kept as a list rather than collapsed to a winner in the runner, because the API stores the
 * runner-up as the fallback and a person may pick a different one entirely. The runner reports;
 * choosing is a decision, and decisions live above it.
 */
export interface LocatorCandidate {
  strategy: LocatorStrategy;
  value: string;
  /** Disambiguates a strategy that needs two parts — `role` with an accessible name. */
  name?: string;
  /** How many nodes this matched. 1 is the only good answer. */
  matches: number;
  /** 0–1. Uniqueness, stability and semantics, combined — see `score.ts`. */
  score: number;
}

/** One interactive element found on the page. */
export interface InspectedElement {
  /**
   * A suggested identifier, lowerCamelCase — `submitButton`, `emailInput`.
   *
   * A suggestion because the IR may already reference a different name for the same thing; the
   * API matches by name and a person can rename. Derived from the accessible name and the role,
   * which is what a human would have called it anyway.
   */
  name: string;
  /** `button`, `textbox`, `link` … the ARIA role, which is what makes a name meaningful. */
  role?: string;
  /** What a screen reader would announce. The single most useful fact about an element. */
  accessibleName?: string;
  /** `input`, `button`, `a` … kept because a role is sometimes absent and a tag never is. */
  tag: string;
  /** Best first. The API takes [0] as the locator and [1] as the fallback. */
  candidates: LocatorCandidate[];
}

export interface InspectResult {
  pageName: string;
  /** Where the browser actually ended up — a redirect makes this differ from the request. */
  url: string;
  title: string;
  elements: InspectedElement[];
  /**
   * Elements that were found but could not be addressed uniquely by any strategy.
   *
   * Reported rather than dropped: "there are three buttons I cannot tell apart" is something a
   * person can fix in their application or work around, and silently omitting them would leave
   * the user wondering why their button never appears.
   */
  ambiguous: string[];
}
