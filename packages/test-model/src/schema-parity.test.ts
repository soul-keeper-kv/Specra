import { describe, expect, it } from "vitest";

import {
  ACTIONS,
  CONDITIONS,
  PARAMETER_TYPES,
  SELECTOR_STRATEGIES,
  VALUE_KINDS,
} from "./types.js";
import { testModelSchema } from "./validate.js";

/**
 * The schema is the source of truth and `types.ts` is a hand-written mirror of it, so the
 * closed vocabularies are the one thing that could drift without anything failing. They
 * cannot: an action added to the schema and not to `ACTIONS` fails here.
 */
const schema = testModelSchema as unknown as Record<string, any>;

function enumAt(pointer: string): string[] {
  const value = pointer
    .split("/")
    .filter(Boolean)
    .reduce<any>((node, key) => node?.[key], schema);
  expect(value, `no enum at ${pointer}`).toBeDefined();
  return value as string[];
}

describe("types.ts mirrors the schema", () => {
  it("declares exactly the schema's actions", () => {
    expect([...ACTIONS].sort()).toEqual(
      [...enumAt("/$defs/step/properties/action/enum")].sort(),
    );
  });

  it("declares exactly the schema's assertion conditions", () => {
    expect([...CONDITIONS].sort()).toEqual(
      [...enumAt("/$defs/assertion/properties/condition/enum")].sort(),
    );
  });

  it("declares exactly the schema's parameter types", () => {
    expect([...PARAMETER_TYPES].sort()).toEqual(
      [...enumAt("/$defs/parameter/properties/type/enum")].sort(),
    );
  });

  it("declares exactly the schema's selector strategies", () => {
    expect([...SELECTOR_STRATEGIES].sort()).toEqual(
      [...enumAt("/$defs/selectorTarget/properties/selector/properties/strategy/enum")].sort(),
    );
  });

  it("declares exactly the schema's value kinds", () => {
    const literal = schema.$defs.value.oneOf[0].properties.kind.const as string;
    const named = schema.$defs.value.oneOf[1].properties.kind.enum as string[];
    expect([...VALUE_KINDS].sort()).toEqual([literal, ...named].sort());
  });

  it("is the version this package claims", () => {
    expect(schema.properties.irVersion.const).toBe(1);
    expect(schema.$id).toMatch(/test-model\.v1\.schema\.json$/);
  });
});
