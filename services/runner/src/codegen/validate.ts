/**
 * The gate between "generated" and "proposed".
 *
 * A generation that does not compile, does not lint, or is not formatted the way the rest of
 * the repository is formatted must never reach a user's branch — the acceptance test for the
 * whole product is that a cloned repository runs with no reference to Specra, and code an
 * automation engineer has to reformat before they can read a diff fails that in spirit.
 *
 * **Deliberately not inside `generate()`.** The adapter is a pure function — no clock, no
 * random, no filesystem — and eslint and tsc are neither: they need a real project on disk
 * with real engine types.
 *
 * Prettier is pure, so formatting *could* have folded into the adapter — except that
 * prettier 3 is async-only, and awaiting inside `generate()` would make the one function the
 * product's determinism rests on a promise, rippling `await` through every call site and
 * every golden test for no gain in what the bytes are. So the adapter emits code it has
 * already written correctly, and this module both formats and asserts that the two agree:
 * {@link checkFormatting} failing means an adapter template drifted, which is a bug to fix in
 * the template rather than paper over on the way out.
 */

import { mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

import * as prettier from "prettier";

import type { GeneratedFile } from "./types.js";

/** What the repository's own `.prettierrc.json` says, restated so a generated project is
 * formatted the same way whether or not it is being written inside this repository. A
 * generated project is the *user's*, and it carries no Specra config file — so the settings
 * have to travel in the bytes rather than in a file the user did not ask for. */
const PRETTIER_OPTIONS: prettier.Options = {
  semi: true,
  singleQuote: false,
  trailingComma: "all",
  printWidth: 96,
  tabWidth: 2,
};

/** Only these are worth running a linter or a compiler over; JSON is prettier's business. */
const TS_EXTENSION = ".ts";

export interface ValidationProblem {
  path: string;
  message: string;
}

export interface ValidationResult {
  ok: boolean;
  problems: ValidationProblem[];
}

/**
 * Formats every generated file with the project's prettier settings.
 *
 * Called by the job layer on its way out, so what the API receives is what prettier would have
 * written — the alternative is an adapter that emits *almost* formatted code and a check that
 * reports the difference, which tells a user their generated file is wrong when the truth is
 * that our template had a trailing space. Prettier is deterministic, so the golden files
 * taken after it stay byte-stable.
 */
export async function formatFiles(files: GeneratedFile[]): Promise<GeneratedFile[]> {
  const formatted: GeneratedFile[] = [];
  for (const file of files) {
    const parser = parserFor(file.path);
    if (!parser) {
      formatted.push(file);
      continue;
    }
    formatted.push({
      ...file,
      contents: await prettier.format(file.contents, { ...PRETTIER_OPTIONS, parser }),
    });
  }
  return formatted;
}

/**
 * True when every file is already exactly what prettier would produce.
 *
 * The `--check` half of the M3 criterion. With {@link formatFiles} running inside generation
 * this should never fail, which is the point: it is the assertion that formatting is a
 * property of the output and not a step someone can forget.
 */
export async function checkFormatting(files: GeneratedFile[]): Promise<ValidationResult> {
  const problems: ValidationProblem[] = [];
  for (const file of files) {
    const parser = parserFor(file.path);
    if (!parser) continue;
    const ok = await prettier.check(file.contents, { ...PRETTIER_OPTIONS, parser });
    if (!ok) {
      problems.push({
        path: file.path,
        message: "is not formatted as prettier would write it",
      });
    }
  }
  return { ok: problems.length === 0, problems };
}

/**
 * Runs eslint over a materialised project directory.
 *
 * Takes a directory rather than the files because a linter with type information has to
 * resolve imports, and half the rules worth having are the ones that need it. The caller
 * writes the project out (see `portability.test.ts`) and points this at it.
 *
 * The config travels as an argument rather than living in the generated project: `.specra/`
 * is metadata the user may delete, and a generated `eslint.config.mjs` would be Specra
 * imposing its lint policy on someone else's repository.
 */
export async function lintProject(dir: string, configPath: string): Promise<ValidationResult> {
  // The API rather than the CLI: eslint 9 does not export ./bin/eslint.js, and shelling out
  // would only re-parse what the library already returns as data.
  const { ESLint } = await import("eslint");
  const eslint = new ESLint({
    cwd: dir,
    overrideConfigFile: configPath,
    errorOnUnmatchedPattern: false,
  });

  const results = await eslint.lintFiles(["."]);
  const problems: ValidationProblem[] = [];
  for (const result of results) {
    for (const message of result.messages) {
      // Warnings are not failures, but an error is: it is a template bug reaching a user.
      if (message.severity < 2) continue;
      problems.push({
        path: path.relative(dir, result.filePath).replace(/\\/g, "/"),
        message: `${message.line}:${message.column} ${message.message} (${message.ruleId ?? "error"})`,
      });
    }
  }
  return { ok: problems.length === 0, problems };
}

/**
 * Writes an eslint flat config into `dir` and returns its path.
 *
 * Typed linting over generated code, with the rules that catch what a template gets wrong —
 * an unused import left by a step that no longer emits, a floating promise where an `await`
 * was dropped. Rules about style are prettier's job and are not repeated here.
 */
export function writeLintConfig(dir: string, tsconfigPath: string): string {
  const configPath = path.join(dir, "eslint.generated.config.mjs");
  const require_ = createRequire(import.meta.url);
  const js = pathToUrl(require_.resolve("@eslint/js"));
  const tseslint = pathToUrl(require_.resolve("typescript-eslint"));

  writeFileSync(
    configPath,
    `import js from ${JSON.stringify(js)};
import tseslint from ${JSON.stringify(tseslint)};

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        project: ${JSON.stringify(path.basename(tsconfigPath))},
        tsconfigRootDir: ${JSON.stringify(dir.replace(/\\/g, "/"))},
      },
    },
    rules: {
      // Style is prettier's; these are the ones that catch a template bug.
      "@typescript-eslint/no-unused-vars": "error",
      "@typescript-eslint/no-floating-promises": "error",
      "@typescript-eslint/await-thenable": "error",
      // TypeScript already resolves names, and the rule does not know the DOM lib.
      "no-undef": "off",
      // An uninspected page's goto() throws rather than guessing a URL, so it awaits nothing.
      // It stays async because every caller awaits it and inspection later fills in a real
      // navigation — making it sync would be an API that changes shape when a page is
      // inspected.
      "@typescript-eslint/require-await": "off",
    },
  },
  { ignores: ["eslint.generated.config.mjs"] },
);
`,
  );
  return configPath;
}

/** Writes files into `dir`, creating parent directories — the shared half of every check. */
export function materialise(dir: string, files: GeneratedFile[]): void {
  for (const file of files) {
    const full = path.join(dir, file.path);
    mkdirSync(path.dirname(full), { recursive: true });
    writeFileSync(full, file.contents);
  }
}

function parserFor(filePath: string): "typescript" | "json" | undefined {
  if (filePath.endsWith(TS_EXTENSION)) return "typescript";
  if (filePath.endsWith(".json")) return "json";
  return undefined;
}

/** eslint's flat config is ESM, so a Windows path has to become a file:// URL to import. */
function pathToUrl(filePath: string): string {
  return `file:///${filePath.replace(/\\/g, "/")}`;
}
