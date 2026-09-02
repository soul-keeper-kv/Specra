/**
 * What to read off a page, as a function the browser runs.
 *
 * It is a string-free function rather than an evaluated string so TypeScript checks it, but it
 * executes in the page's context — so it may not close over anything from this module. Every
 * helper it needs is defined inside it. That constraint is why it reads as one long function
 * rather than a tidy set of exports.
 *
 * It collects **facts only**. No scoring, no ranking, no choosing — those happen in `score.ts`,
 * which runs in Node and can therefore be tested without launching anything.
 */

/** One element as the page describes itself, before anything has judged it. */
export interface RawElement {
  tag: string;
  role?: string;
  accessibleName?: string;
  testId?: string;
  label?: string;
  placeholder?: string;
  text?: string;
  altText?: string;
  title?: string;
  id?: string;
  /** A CSS path, as a last-resort candidate. */
  cssPath: string;
  /** How many nodes each fact matches, so the planner can gate on uniqueness. */
  matches: {
    testId?: number;
    role?: number;
    label?: number;
    placeholder?: number;
    text?: number;
    altText?: number;
    title?: number;
    css?: number;
  };
}

/**
 * Just enough of the DOM to typecheck the function below.
 *
 * Declared here rather than by adding `"DOM"` to the package's `lib`: this is a Node service, and
 * a global `document` in scope for every file is how a browser API ends up called on the server.
 * Narrow and local is the point — if this function needs an API that is missing, adding it here
 * is one line and stays confined to the one function that runs in a page.
 */
interface DomElement {
  tagName: string;
  nodeType: number;
  id: string;
  textContent: string | null;
  parentElement: DomElement | null;
  children: ArrayLike<DomElement>;
  getAttribute(name: string): string | null;
  hasAttribute(name: string): boolean;
  getBoundingClientRect(): { width: number; height: number };
  closest(selector: string): DomElement | null;
}

interface DomDocument {
  querySelectorAll<T = DomElement>(selector: string): ArrayLike<T>;
  querySelector(selector: string): DomElement | null;
  getElementById(id: string): DomElement | null;
}

declare const document: DomDocument;
declare const window: {
  getComputedStyle(el: DomElement): { visibility: string; display: string };
};
declare const CSS: { escape(value: string): string };

/**
 * Runs in the page. Takes the test-id attribute name because it is configurable per project.
 *
 * Kept in this file rather than in the adapter because it names no engine API — it is DOM, which
 * every engine shares. The adapter's job is to *transport* it into a page.
 */
export const COLLECT_SCRIPT = (testIdAttribute: string): RawElement[] => {
  const INTERACTIVE =
    "a[href], button, input:not([type=hidden]), select, textarea, [role=button]," +
    " [role=link], [role=checkbox], [role=radio], [role=tab], [role=menuitem]," +
    " [role=textbox], [role=combobox], [contenteditable=true]";

  const nodes = Array.from(document.querySelectorAll<DomElement>(INTERACTIVE));

  const visible = (element: DomElement): boolean => {
    const rect = element.getBoundingClientRect();
    if (rect.width === 0 && rect.height === 0) return false;
    const style = window.getComputedStyle(element);
    return style.visibility !== "hidden" && style.display !== "none";
  };

  /** The role the browser would report — explicit first, then the tag's implicit one. */
  const roleOf = (element: DomElement): string | undefined => {
    const explicit = element.getAttribute("role");
    if (explicit) return explicit;
    const tag = element.tagName.toLowerCase();
    if (tag === "a") return element.hasAttribute("href") ? "link" : undefined;
    if (tag === "button") return "button";
    if (tag === "select") return "combobox";
    if (tag === "textarea") return "textbox";
    if (tag === "input") {
      const type = (element.getAttribute("type") ?? "text").toLowerCase();
      if (type === "checkbox") return "checkbox";
      if (type === "radio") return "radio";
      if (type === "submit" || type === "button" || type === "reset") return "button";
      if (type === "search") return "searchbox";
      return "textbox";
    }
    return undefined;
  };

  /**
   * Roughly what a screen reader announces.
   *
   * aria-label, then aria-labelledby, then an associated <label>, then the element's own text,
   * then value/alt/title — the order the accessible-name algorithm uses, simplified to the parts
   * that matter for addressing an element.
   */
  const accessibleNameOf = (element: DomElement): string | undefined => {
    const aria = element.getAttribute("aria-label");
    if (aria?.trim()) return aria.trim();

    const labelledBy = element.getAttribute("aria-labelledby");
    if (labelledBy) {
      const text = labelledBy
        .split(/\s+/)
        .map((id) => document.getElementById(id)?.textContent?.trim() ?? "")
        .filter(Boolean)
        .join(" ");
      if (text) return text;
    }

    if (element.id) {
      const label = document.querySelector(`label[for="${CSS.escape(element.id)}"]`);
      if (label?.textContent?.trim()) return label.textContent.trim();
    }
    const wrapping = element.closest("label");
    if (wrapping?.textContent?.trim()) return wrapping.textContent.trim();

    const own = element.textContent?.trim();
    if (own) return own;

    const value = element.getAttribute("value");
    if (value?.trim()) return value.trim();
    const alt = element.getAttribute("alt");
    if (alt?.trim()) return alt.trim();
    const title = element.getAttribute("title");
    if (title?.trim()) return title.trim();
    return undefined;
  };

  /**
   * A CSS path, stopping at the nearest id.
   *
   * Only ever a last-resort candidate, and the scorer knows to distrust it — but "no locator at
   * all" helps nobody, and a path is at least something a person can look at and correct.
   */
  const cssPathOf = (element: DomElement): string => {
    const parts: string[] = [];
    let node: DomElement | null = element;
    while (node && node.nodeType === 1 && parts.length < 6) {
      if (node.id) {
        parts.unshift(`#${CSS.escape(node.id)}`);
        break;
      }
      const tag = node.tagName.toLowerCase();
      const parent: DomElement | null = node.parentElement;
      if (!parent) {
        parts.unshift(tag);
        break;
      }
      const siblings = Array.from(parent.children).filter(
        (child) => child.tagName === node!.tagName,
      );
      parts.unshift(
        siblings.length > 1 ? `${tag}:nth-of-type(${siblings.indexOf(node) + 1})` : tag,
      );
      node = parent;
    }
    return parts.join(" > ");
  };

  const count = (selector: string): number => {
    try {
      return document.querySelectorAll(selector).length;
    } catch {
      // An unescapable value — a quote in an attribute. Reported as 0 so the planner drops it
      // rather than emitting a selector that would throw in the generated test.
      return 0;
    }
  };

  /** How many elements share this visible text, so `text` can be gated on uniqueness. */
  const textMatches = (text: string): number =>
    nodes.filter((node) => node.textContent?.trim() === text).length;

  const results: RawElement[] = [];

  for (const element of nodes) {
    if (!visible(element)) continue;

    const testId = element.getAttribute(testIdAttribute) ?? undefined;
    const role = roleOf(element);
    const accessibleName = accessibleNameOf(element);
    const placeholder = element.getAttribute("placeholder") ?? undefined;
    const altText = element.getAttribute("alt") ?? undefined;
    const title = element.getAttribute("title") ?? undefined;
    // Long text is a paragraph that happens to be clickable, not a label. Addressing an element
    // by 300 characters of prose is brittle in a way the length penalty alone would not catch.
    const own = element.textContent?.trim();
    const text = own && own.length <= 60 ? own : undefined;

    const label =
      element.id &&
      document.querySelector(`label[for="${CSS.escape(element.id)}"]`)?.textContent?.trim();

    results.push({
      tag: element.tagName.toLowerCase(),
      role,
      accessibleName,
      testId,
      label: label || undefined,
      placeholder,
      text,
      altText,
      title,
      id: element.id || undefined,
      cssPath: cssPathOf(element),
      matches: {
        testId: testId ? count(`[${testIdAttribute}="${CSS.escape(testId)}"]`) : undefined,
        // A role is only addressable together with its accessible name; the count that matters
        // is how many elements share both.
        role:
          role && accessibleName
            ? nodes.filter(
                (node) => roleOf(node) === role && accessibleNameOf(node) === accessibleName,
              ).length
            : undefined,
        label: label ? nodes.filter((n) => accessibleNameOf(n) === label).length : undefined,
        placeholder: placeholder
          ? count(`[placeholder="${CSS.escape(placeholder)}"]`)
          : undefined,
        text: text ? textMatches(text) : undefined,
        altText: altText ? count(`[alt="${CSS.escape(altText)}"]`) : undefined,
        title: title ? count(`[title="${CSS.escape(title)}"]`) : undefined,
        css: count(cssPathOf(element)),
      },
    });
  }

  return results;
};
