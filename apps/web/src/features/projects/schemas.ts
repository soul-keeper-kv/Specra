import type { useTranslations } from "next-intl";
import { z } from "zod";

type ProjectValidationMessages = ReturnType<typeof useTranslations<"projects.validation">>;

/**
 * Mirrors the bean validation on `ProjectRequest` so the user sees an error before a round trip.
 * Built from a translator, never at module scope — a schema evaluated on import would freeze
 * whichever language loaded first. Callers wrap it in `useMemo` keyed on the translator.
 */
export function buildProjectSchema(t: ProjectValidationMessages) {
  return z.object({
    name: z.string().trim().min(1, t("nameRequired")).max(120, t("nameMax")),
    // Typed lower-case is fine — the value is upper-cased before it is sent.
    key: z
      .string()
      .trim()
      .max(16, t("keyMax"))
      .regex(/^$|^[A-Za-z][A-Za-z0-9]*$/, t("keyFormat")),
    description: z.string().trim().max(2000, t("descriptionMax")),
  });
}

export type ProjectFormValues = z.infer<ReturnType<typeof buildProjectSchema>>;
