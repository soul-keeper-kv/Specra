/**
 * `(IR, page objects, options) → files`. A pure function, and the product depends on it staying
 * one.
 *
 * No model call, no clock, no random, no filesystem read. The intelligence happened earlier,
 * when the IR was built; this is templating, and templating that compiles beats prose that
 * sometimes compiles. `golden.test.ts` is what holds the line: same IR in, identical bytes out.
 *
 * The one judgement it does make is refusing: a step that names a page nobody inspected comes
 * back in `unresolved`, and the caller turns that into "inspect this page first" rather than a
 * spec with a guessed locator in it.
 */

import { validateTestModel } from "@specra/test-model";

import {
  configFileName,
  renderEnvironmentFixture,
  renderFlow,
  renderPackageJson,
  renderPageObject,
  renderPlaywrightConfig,
  renderProjectDescriptor,
  renderSpec,
} from "../adapters/playwright/index.js";
import { flowPath, modelPath, pagePath, specPath } from "./naming.js";
import type { CodegenRequest, CodegenResult, GeneratedFile } from "./types.js";

const DEFAULT_BROWSERS = ["chromium"];

/** The adapter names its own config file; this module only decides that one gets written. */
const CONFIG_FILE = configFileName();

export function generate(request: CodegenRequest): CodegenResult {
  // The IR arrives validated from apps/api, but this is a public entry point of another
  // runtime: a document that failed the schema must not become source code because a caller
  // forgot to check.
  const validation = validateTestModel(request.model);
  if (!validation.valid) {
    const first = validation.violations[0];
    throw new Error(
      `The Test Model is not valid: ${first ? `${first.path} ${first.message}` : "unknown violation"}`,
    );
  }

  const { model, pages, flows = [], options } = request;

  // Two page objects of the same name would write the same path twice, and which body survived
  // would depend on whoever consumed the list last. That is a silently wrong repository, so it
  // is refused here rather than resolved by a rule nobody asked for.
  const duplicate = pages
    .map((page) => page.name)
    .find((name, index, all) => all.indexOf(name) !== index);
  if (duplicate) {
    throw new Error(
      `Two page objects are both named ${duplicate}; each page must appear once.`,
    );
  }

  const known = new Map(
    pages.map((page) => [page.name, new Set(page.elements.map((element) => element.name))]),
  );
  const files: GeneratedFile[] = [];

  const spec = renderSpec(request);
  files.push({
    path: specPath(model.name, options.area),
    contents: spec.contents,
    role: "SPEC",
  });

  // Only the pages this test actually touches: a generation is a diff, and a diff that
  // rewrites every page object in the repository is a diff nobody reviews.
  for (const page of pages) {
    files.push({ path: pagePath(page.name), contents: renderPageObject(page), role: "PAGE" });
  }

  // A flow's unresolved pages matter as much as a spec's: the flow is real code in the same
  // repository, and a page nobody inspected must stop the generation either way.
  const unresolved = [...spec.unresolved];
  for (const flow of flows) {
    const rendered = renderFlow(flow.name, flow.model, known);
    unresolved.push(...rendered.unresolved);
    files.push({ path: flowPath(flow.name), contents: rendered.contents, role: "FIXTURE" });
  }

  // Every parameter this generation can reach — the case's own and every flow's.
  //
  // `fixtures/environment.ts` is one file shared by the whole project, so writing only this
  // case's parameters would delete the accessors the other specs import. The caller merges this
  // file with what the repository already has; within one generation, the union is the least it
  // can be. Sorted by name so two runs over the same inputs cannot differ.
  const parameters = [...(model.parameters ?? [])];
  for (const flow of flows) {
    for (const parameter of flow.model.parameters ?? []) {
      if (!parameters.some((existing) => existing.name === parameter.name)) {
        parameters.push(parameter);
      }
    }
  }
  parameters.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));

  if (parameters.length > 0) {
    files.push({
      path: "fixtures/environment.ts",
      contents: renderEnvironmentFixture(parameters),
      role: "FIXTURE",
    });
  }

  if (options.scaffold) {
    const browsers = options.browsers ?? DEFAULT_BROWSERS;
    files.push({
      path: "package.json",
      contents: renderPackageJson(options.projectName ?? "automation-tests"),
      role: "CONFIG",
    });
    files.push({
      path: CONFIG_FILE,
      contents: renderPlaywrightConfig(browsers),
      role: "CONFIG",
    });
    files.push({
      path: ".specra/project.json",
      contents: renderProjectDescriptor(options.adapterVersion, browsers),
      role: "CONFIG",
    });
  }

  // The IR, committed beside the code it produced, so the two never drift in history.
  files.push({
    path: modelPath(options.reference),
    contents: `${JSON.stringify(model, null, 2)}\n`,
    role: "MODEL",
  });

  files.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
  return { files, unresolved };
}
