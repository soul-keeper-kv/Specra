/**
 * A locator, in Playwright's words.
 *
 * This file and its siblings are the only place in the repository where those words are
 * allowed. Everything above it says `testId` and `role`; the mapping to `getByTestId` and
 * `getByRole` happens here so a second engine is a second folder, not a rewrite.
 */

import type { ElementLocator, LocatorStrategy } from "../../codegen/types.js";
import { stringLiteral } from "../../codegen/naming.js";

/** `page.getByRole("button", { name: "Log in" })`, as source text. */
export function locatorExpression(locator: ElementLocator, root = "this.page"): string {
  return call(root, locator.strategy, locator.value, locator.name_);
}

export function fallbackExpression(locator: ElementLocator, root = "this.page"): string | null {
  if (!locator.fallback) {
    return null;
  }
  return call(root, locator.fallback.strategy, locator.fallback.value, undefined);
}

/** A raw selector from the IR's escape hatch — `css` and `xpath` are all it allows. */
export function rawSelectorExpression(
  strategy: "css" | "xpath",
  value: string,
  root = "page",
): string {
  return strategy === "xpath"
    ? `${root}.locator(${stringLiteral(`xpath=${value}`)})`
    : `${root}.locator(${stringLiteral(value)})`;
}

function call(
  root: string,
  strategy: LocatorStrategy,
  value: string,
  accessibleName: string | undefined,
): string {
  const text = stringLiteral(value);
  switch (strategy) {
    case "testId":
      return `${root}.getByTestId(${text})`;
    case "role":
      return accessibleName
        ? `${root}.getByRole(${text}, { name: ${stringLiteral(accessibleName)} })`
        : `${root}.getByRole(${text})`;
    case "label":
      return `${root}.getByLabel(${text})`;
    case "placeholder":
      return `${root}.getByPlaceholder(${text})`;
    case "text":
      return `${root}.getByText(${text})`;
    case "altText":
      return `${root}.getByAltText(${text})`;
    case "title":
      return `${root}.getByTitle(${text})`;
    case "xpath":
      return `${root}.locator(${stringLiteral(`xpath=${value}`)})`;
    case "css":
      return `${root}.locator(${text})`;
  }
}
