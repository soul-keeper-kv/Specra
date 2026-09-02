import type { RawElement } from "./collect.js";
import { rankCandidates, scoreCandidate } from "./score.js";
import type { InspectedElement, LocatorCandidate } from "./types.js";

/**
 * Facts about a page become ranked ways of addressing it.
 *
 * Pure: raw elements in, scored candidates out, no browser and no I/O. That is the whole reason
 * `collect.ts` reports rather than decides — the judgement in this file is the part worth
 * testing, and it must be testable without launching anything.
 */
export function planElements(raw: RawElement[]): {
  elements: InspectedElement[];
  ambiguous: string[];
} {
  const elements: InspectedElement[] = [];
  const ambiguous: string[] = [];
  const used = new Set<string>();

  for (const element of raw) {
    const candidates = rankCandidates(candidatesFor(element));

    if (candidates.length === 0) {
      // Found, but not addressable by anything unique. Reported rather than dropped: "there are
      // three buttons I cannot tell apart" is something a person can act on, and silence would
      // leave them wondering why their button never appeared.
      ambiguous.push(describe(element));
      continue;
    }

    elements.push({
      name: uniqueName(suggestName(element), used),
      role: element.role,
      accessibleName: element.accessibleName,
      tag: element.tag,
      candidates,
    });
  }

  return { elements, ambiguous };
}

/** Every way this element could be addressed, scored. Uniqueness is gated inside the scorer. */
function candidatesFor(element: RawElement): LocatorCandidate[] {
  const candidates: LocatorCandidate[] = [];
  const add = (
    strategy: LocatorCandidate["strategy"],
    value: string | undefined,
    matches: number | undefined,
    name?: string,
  ) => {
    if (!value || matches === undefined) return;
    candidates.push({
      strategy,
      value,
      ...(name ? { name } : {}),
      matches,
      score: scoreCandidate(strategy, value, matches),
    });
  };

  add("testId", element.testId, element.matches.testId);
  // `role` carries its accessible name as a second part — that pairing is what makes it unique,
  // and the generated code addresses it as a role plus that name.
  add("role", element.role, element.matches.role, element.accessibleName);
  add("label", element.label, element.matches.label);
  add("placeholder", element.placeholder, element.matches.placeholder);
  add("text", element.text, element.matches.text);
  add("altText", element.altText, element.matches.altText);
  add("title", element.title, element.matches.title);
  add("css", element.id ? `#${element.id}` : element.cssPath, element.matches.css);

  return candidates;
}

/**
 * What a human would have called this element.
 *
 * The accessible name plus the role — `Sign in` on a button becomes `signInButton` — because
 * that is what a person writing the page object by hand would type, and the IR references
 * elements by name.
 */
function suggestName(element: RawElement): string {
  const base =
    element.accessibleName ??
    element.label ??
    element.placeholder ??
    element.testId ??
    element.tag;

  const suffix = suffixFor(element.role, element.tag);
  const words = base
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .split(/\s+/)
    .slice(0, 4)
    .filter(Boolean);

  if (words.length === 0) return suffix || "element";

  const camel = words
    .map((word, index) =>
      index === 0
        ? word.toLowerCase()
        : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase(),
    )
    .join("");

  // A name must be a valid identifier: a leading digit is not, and "2faCode" would not compile.
  const identifier = /^\p{L}/u.test(camel)
    ? camel
    : `e${camel.charAt(0).toUpperCase()}${camel.slice(1)}`;

  // Do not append "Button" to something already called "submitButton".
  if (!suffix || identifier.toLowerCase().endsWith(suffix.toLowerCase())) return identifier;
  return identifier + suffix.charAt(0).toUpperCase() + suffix.slice(1);
}

function suffixFor(role: string | undefined, tag: string): string {
  switch (role) {
    case "button":
      return "Button";
    case "link":
      return "Link";
    case "textbox":
    case "searchbox":
      return "Input";
    case "combobox":
      return "Select";
    case "checkbox":
      return "Checkbox";
    case "radio":
      return "Radio";
    case "tab":
      return "Tab";
    default:
      return tag === "a" ? "Link" : "";
  }
}

/**
 * Two "Delete" buttons in a table would both be `deleteButton`, and a page object cannot have
 * two members of one name. The second becomes `deleteButton2` — ugly, and better than a
 * generation that fails to compile for a reason nobody can see.
 */
function uniqueName(name: string, used: Set<string>): string {
  if (!used.has(name)) {
    used.add(name);
    return name;
  }
  let index = 2;
  while (used.has(`${name}${index}`)) index++;
  const unique = `${name}${index}`;
  used.add(unique);
  return unique;
}

/** Enough for a person to find the element in their own page. */
function describe(element: RawElement): string {
  const name = element.accessibleName ?? element.text ?? element.placeholder;
  return name ? `${element.tag} "${name}"` : `${element.tag} at ${element.cssPath}`;
}
