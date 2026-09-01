import { describe, expect, it } from "vitest";

import { loadFixtures } from "./fixtures.js";
import { validateTestModel } from "./validate.js";

describe("the v1 schema", () => {
  const valid = loadFixtures("valid");
  const schemaInvalid = loadFixtures("invalid/schema");
  const semanticInvalid = loadFixtures("invalid/semantic");

  it("has fixtures to check itself against", () => {
    expect(valid.length).toBeGreaterThan(0);
    expect(schemaInvalid.length).toBeGreaterThan(0);
    expect(semanticInvalid.length).toBeGreaterThan(0);
  });

  it.each(valid)("accepts $name", ({ document }) => {
    const result = validateTestModel(document);
    expect(result.violations).toEqual([]);
    expect(result.valid).toBe(true);
  });

  it.each(schemaInvalid)("rejects $name", ({ document }) => {
    expect(validateTestModel(document).valid).toBe(false);
  });

  it.each(semanticInvalid)(
    "lets $name through — it is the semantic layer's to reject",
    ({ document }) => {
      expect(validateTestModel(document).valid).toBe(true);
    },
  );

  it("points at the step that broke the rule", () => {
    const result = validateTestModel({
      irVersion: 1,
      name: "Type into the username field",
      steps: [
        {
          id: "s1",
          sourceStepIds: ["ts-1"],
          action: "fill",
          target: { page: "LoginPage", element: "usernameInput" },
        },
      ],
    });

    expect(result.valid).toBe(false);
    if (result.valid) return;
    expect(result.violations.some((violation) => violation.path.startsWith("/steps/0"))).toBe(
      true,
    );
  });

  it("cannot express a duration, whatever the field is called", () => {
    for (const field of ["timeout", "sleep", "waitMs", "delay"]) {
      const result = validateTestModel({
        irVersion: 1,
        name: "Wait a while",
        steps: [
          {
            id: "s1",
            sourceStepIds: ["ts-1"],
            action: "waitFor",
            target: { page: "HomePage", element: "banner" },
            assertion: { condition: "visible" },
            [field]: 3000,
          },
        ],
      });
      expect(result.valid, `${field} should not be allowed on a step`).toBe(false);
    }
  });
});
