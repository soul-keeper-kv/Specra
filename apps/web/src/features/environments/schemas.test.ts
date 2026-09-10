import { describe, expect, it } from "vitest";

import { buildEnvironmentSchema, type EnvironmentFormValues } from "./schemas";

/**
 * The translator the schema is built from. The messages themselves are checked by the bundle
 * parity test; here only the key matters, so echoing it back identifies which rule fired.
 */
const t = ((key: string) => key) as unknown as Parameters<typeof buildEnvironmentSchema>[0];
const schema = buildEnvironmentSchema(t);

function values(overrides: Partial<EnvironmentFormValues> = {}): EnvironmentFormValues {
  return {
    name: "STAGING",
    baseUrl: "https://staging.example.com",
    isDefault: false,
    variables: [],
    preludeTestCaseId: "",
    ...overrides,
  };
}

function messages(input: EnvironmentFormValues): string[] {
  const result = schema.safeParse(input);
  return result.success ? [] : result.error.issues.map((issue) => issue.message);
}

describe("environment schema", () => {
  it("accepts a minimal environment", () => {
    expect(messages(values())).toEqual([]);
  });

  it("requires an http(s) base URL", () => {
    // A bare host is the plausible typo: it parses as a string and fails only at dispatch.
    expect(messages(values({ baseUrl: "staging.example.com" }))).toContain("baseUrlFormat");
  });

  it("rejects a variable key a process environment could not hold", () => {
    expect(
      messages(
        values({ variables: [{ key: "qa-user", value: "x", secret: false, stored: false }] }),
      ),
    ).toContain("keyFormat");
  });

  /**
   * The API replaces the whole variable list and would store both rows happily, of which only
   * one can reach a process environment. There is no server counterpart, which is the point.
   */
  it("rejects two variables sharing a key", () => {
    const duplicated = values({
      variables: [
        { key: "QA_USER", value: "a", secret: false, stored: false },
        { key: "QA_USER", value: "b", secret: false, stored: false },
      ],
    });
    expect(messages(duplicated)).toContain("keyUnique");
  });

  it("requires a value for a secret that has none stored", () => {
    const blank = values({
      variables: [{ key: "TOKEN", value: "", secret: true, stored: false }],
    });
    expect(messages(blank)).toContain("secretRequired");
  });

  /**
   * The rule the edit form rests on: a stored secret comes back with no value, so an empty
   * field means "keep the stored one" rather than "store an empty secret".
   */
  it("accepts a stored secret left blank", () => {
    const stored = values({
      variables: [{ key: "TOKEN", value: "", secret: true, stored: true }],
    });
    expect(messages(stored)).toEqual([]);
  });
});
