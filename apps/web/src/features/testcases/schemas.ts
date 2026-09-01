import type { useTranslations } from "next-intl";
import { z } from "zod";

type TestCaseValidationMessages = ReturnType<typeof useTranslations<"testcases.validation">>;

/**
 * Mirrors the bean validation on `TestCaseRequest` so the author sees an error before a round
 * trip. Built from a translator, never at module scope, and wrapped in `useMemo` by callers.
 */
export function buildTestCaseSchema(t: TestCaseValidationMessages) {
  return z.object({
    title: z.string().trim().min(1, t("titleRequired")).max(200, t("titleMax")),
    description: z.string().trim().max(10000, t("descriptionMax")),
    preconditions: z.string().trim().max(10000, t("preconditionsMax")),
    expectedResult: z.string().trim().max(10000, t("expectedResultMax")),
    priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
    steps: z
      .array(
        z.object({
          action: z
            .string()
            .trim()
            .min(1, t("stepActionRequired"))
            .max(2000, t("stepActionMax")),
          expected: z.string().trim().max(2000, t("stepExpectedMax")),
        }),
      )
      .max(100, t("stepsMax")),
    tags: z.array(z.string().trim().max(64, t("tagMax"))).max(20, t("tagsMax")),
  });
}

export type TestCaseFormValues = z.infer<ReturnType<typeof buildTestCaseSchema>>;
