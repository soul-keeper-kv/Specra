import Ajv2020, { type ErrorObject, type ValidateFunction } from "ajv/dist/2020.js";

import schema from "../schema/test-model.v1.schema.json" with { type: "json" };
import type { TestModel } from "./types.js";

export { schema as testModelSchema };

export interface SchemaViolation {
  /** JSON Pointer into the document, e.g. `/steps/2/value`. */
  path: string;
  message: string;
}

export type SchemaValidationResult =
  | { valid: true; document: TestModel; violations: [] }
  | { valid: false; violations: SchemaViolation[] };

let compiled: ValidateFunction | undefined;

function validator(): ValidateFunction {
  if (!compiled) {
    const ajv = new Ajv2020({ allErrors: true, strict: true, strictTypes: false });
    compiled = ajv.compile(schema);
  }
  return compiled;
}

function describe(error: ErrorObject): SchemaViolation {
  const path = error.instancePath === "" ? "/" : error.instancePath;
  const property = error.params?.["additionalProperty"];
  const message =
    typeof property === "string"
      ? `${error.message} (${property})`
      : (error.message ?? "is invalid");
  return { path, message };
}

/**
 * Structural validation against the v1 schema.
 *
 * This is the half of validation that both runtimes share. The referential and semantic
 * layers — does this page object exist, is every parameter declared, does the test assert
 * anything at all — run where the IR is authored and stored, in `apps/api`.
 */
export function validateTestModel(document: unknown): SchemaValidationResult {
  const validate = validator();
  if (validate(document)) {
    return { valid: true, document: document as TestModel, violations: [] };
  }
  return {
    valid: false,
    violations: (validate.errors ?? []).map(describe),
  };
}

/** Convenience for callers that only need the verdict. */
export function isTestModel(document: unknown): document is TestModel {
  return validateTestModel(document).valid;
}
