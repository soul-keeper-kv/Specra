/**
 * Names and text, decided the same way every time.
 *
 * Every function here is pure and total: the same IR must produce the same file names on every
 * machine, so nothing may consult a locale, a clock or the filesystem. `toLocaleLowerCase` and
 * friends are deliberately absent — they are locale-sensitive, and the Turkish dotless i would
 * be enough to break byte-identical output.
 */

/** `Login with valid credentials` → `login-with-valid-credentials`. */
export function kebabCase(text: string): string {
  const ascii = text.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D");
  return (
    ascii
      .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "") || "test"
  );
}

/** `login` → `Login`, for a flow's exported function name. */
export function upperFirst(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toUpperCase() + text.slice(1);
}

export function lowerFirst(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toLowerCase() + text.slice(1);
}

/** `CartPage` → `cartPage`: the fixture name a spec destructures. */
export function pageVariable(pageName: string): string {
  return lowerFirst(pageName);
}

/**
 * A TypeScript single-quoted-free string literal.
 *
 * Double quotes to match the repo's prettier config, and every escape spelled out so a value
 * containing a quote, a backslash or a newline cannot end the literal early. A generated file
 * that does not parse is the one bug this whole package exists to make impossible.
 */
export function stringLiteral(value: string): string {
  const escaped = value
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\t/g, "\\t")
    // U+2028/U+2029 are line terminators to a JS parser but invisible in an editor.
    .replace(/\u2028/g, "\\u2028")
    .replace(/\u2029/g, "\\u2029");
  return `"${escaped}"`;
}

/** A literal for any JSON-ish scalar the IR allows in a value or an expectation. */
export function scalarLiteral(value: string | number | boolean): string {
  return typeof value === "string" ? stringLiteral(value) : String(value);
}

/**
 * Wraps free text as a `//` comment that cannot stop being one.
 *
 * Every character a JavaScript parser treats as a line terminator has to go, and that is four
 * of them, not two: `\n`, `\r`, and — the ones an editor renders as ordinary spaces — U+2028 and
 * U+2029. A description carrying U+2028 would otherwise end the comment mid-line and turn
 * whatever followed into live code in the user's repository. `*​/` is stripped for the same
 * reason: this text also reaches a doc comment.
 */
export function lineComment(text: string): string {
  return `// ${flatten(text)}`;
}

/** The same guarantee for text placed inside a `/** … *​/` block. */
export function docComment(text: string): string {
  return flatten(text);
}

function flatten(text: string): string {
  return text
    .replace(/[\n\r\u2028\u2029]+/g, " ")
    .replace(/\*\//g, "* /")
    .trim();
}

/**
 * `tests/auth/login-with-valid-credentials.spec.ts`.
 *
 * The area comes from the case's first tag, so specs group the way the test cases do; without
 * one the spec sits at the root of `tests/` rather than in a folder named after nothing.
 */
export function specPath(name: string, area?: string): string {
  const file = `${kebabCase(name)}.spec.ts`;
  return area ? `tests/${kebabCase(area)}/${file}` : `tests/${file}`;
}

export function pagePath(pageName: string): string {
  return `pages/${pageName}.ts`;
}

export function flowPath(flowName: string): string {
  return `flows/${kebabCase(flowName)}.flow.ts`;
}

/** The IR, committed beside the code it produced. */
export function modelPath(reference: string): string {
  return `.specra/models/${reference}.json`;
}
