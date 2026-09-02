/**
 * The runner's public surface.
 *
 * Today that is codegen. `inspect` (M8), `execute` (M6) and the job server that `apps/api`
 * calls (M6) land beside it, in their own folders, under the same statelessness rule.
 */

export { generate } from "./codegen/generate.js";
export {
  LOCATOR_STRATEGIES,
  type CodegenRequest,
  type CodegenResult,
  type ElementLocator,
  type FlowDefinition,
  type GeneratedFile,
  type GeneratedFileRole,
  type LocatorStrategy,
  type PageObject,
  type ProjectOptions,
} from "./codegen/types.js";
