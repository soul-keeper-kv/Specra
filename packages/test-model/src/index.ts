export * from "./types.js";
export {
  isTestModel,
  testModelSchema,
  validateTestModel,
  type SchemaValidationResult,
  type SchemaViolation,
} from "./validate.js";

/** The IR version this package describes. A new version is a new schema file, never an edit. */
export const IR_VERSION = 1 as const;
