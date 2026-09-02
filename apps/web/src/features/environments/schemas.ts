import type { useTranslations } from "next-intl";
import { z } from "zod";

type EnvironmentValidationMessages = ReturnType<
  typeof useTranslations<"environments.validation">
>;

/**
 * Mirrors the bean validation on `EnvironmentRequest` and `EnvironmentVariableRequest`, so the
 * user is told before a round trip. Built from a translator rather than at module scope — a
 * schema evaluated on import would freeze whichever language loaded first.
 *
 * The one rule with no server counterpart is `keyUnique`: the API replaces the whole variable
 * list and would happily store two rows with the same key, of which only one can reach a
 * process environment. Catching it here is cheaper than explaining it afterwards.
 */
export function buildEnvironmentSchema(t: EnvironmentValidationMessages) {
  const variable = z.object({
    key: z
      .string()
      .trim()
      .min(1, t("keyRequired"))
      .max(120, t("keyMax"))
      // What a process environment actually accepts, which is what generated code reads.
      .regex(/^[A-Za-z_][A-Za-z0-9_]*$/, t("keyFormat")),
    value: z.string().max(4000, t("valueMax")),
    secret: z.boolean(),
    /**
     * True when this row is an already-stored secret whose value the API never returned. Sending
     * it back with an empty value is how the request says "keep the stored one".
     */
    stored: z.boolean(),
  });

  return z
    .object({
      name: z.string().trim().min(1, t("nameRequired")).max(64, t("nameMax")),
      baseUrl: z
        .string()
        .trim()
        .min(1, t("baseUrlRequired"))
        .max(500, t("baseUrlMax"))
        .regex(/^https?:\/\/.+/, t("baseUrlFormat")),
      isDefault: z.boolean(),
      variables: z.array(variable).max(100, t("variablesMax")),
    })
    .check((ctx) => {
      const seen = new Set<string>();
      ctx.value.variables.forEach((row, index) => {
        const key = row.key.trim();
        if (key && seen.has(key)) {
          ctx.issues.push({
            code: "custom",
            input: row.key,
            message: t("keyUnique"),
            path: ["variables", index, "key"],
          });
        }
        seen.add(key);

        // A brand-new secret has nothing stored to fall back on, which the API answers with
        // `error.environment.secret-required` — a round trip to say what is visible here.
        if (row.secret && !row.stored && row.value.trim() === "") {
          ctx.issues.push({
            code: "custom",
            input: row.value,
            message: t("secretRequired"),
            path: ["variables", index, "value"],
          });
        }
      });
    });
}

export type EnvironmentFormValues = z.infer<ReturnType<typeof buildEnvironmentSchema>>;
export type EnvironmentVariableValues = EnvironmentFormValues["variables"][number];
