/**
 * The Playwright adapter's public surface.
 *
 * Everything under this folder may speak Playwright; nothing above it may. `containment.test.ts`
 * enforces that, which is what keeps a second engine a second folder rather than a rewrite.
 */

export { renderSpec, type SpecRender } from "./spec.js";
export { renderPageObject } from "./page-object.js";
export { renderFlow, flowExportName, flowFileName, type FlowRender } from "./flow.js";
export {
  PLAYWRIGHT_VERSION,
  configFileName,
  engineTestPackage,
  envVariableName,
  renderEnvironmentFixture,
  renderPackageJson,
  renderPlaywrightConfig,
  renderProjectDescriptor,
} from "./scaffold.js";
export { locatorExpression, fallbackExpression, rawSelectorExpression } from "./locators.js";
export { renderStep, type StepContext } from "./steps.js";
export {
  engineCommand,
  engineCliPath,
  engineImage,
  engineEntrypoint,
  engineReportEnv,
  type EngineCommandRequest,
} from "./command.js";
export { readReport } from "./report.js";
