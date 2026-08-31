import type { useTranslations } from "next-intl";
import { z } from "zod";

type NoteValidationMessages = ReturnType<typeof useTranslations<"notes.validation">>;

/**
 * Mirrors the bean validation on `NoteRequest` so the user sees an error before a round trip.
 *
 * Built from a translator rather than declared at module scope: a `z.object` evaluated on import
 * would freeze whichever language happened to load first, and every later locale switch would keep
 * showing that one. Callers wrap this in `useMemo` keyed on the translator.
 */
export function buildNoteSchema(t: NoteValidationMessages) {
  return z.object({
    title: z.string().trim().min(1, t("titleRequired")).max(200, t("titleMax")),
    content: z.string().trim().min(1, t("contentRequired")),
    tags: z.array(z.string().trim().max(64, t("tagMax"))).max(20, t("tagsMax")),
  });
}

export type NoteFormValues = z.infer<ReturnType<typeof buildNoteSchema>>;
