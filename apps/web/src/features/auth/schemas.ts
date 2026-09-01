import type { useTranslations } from "next-intl";
import { z } from "zod";

type AuthValidationMessages = ReturnType<typeof useTranslations<"auth.validation">>;

/** Matches the floor on `RegisterRequest`; the API is still the one that decides. */
export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 128;

/**
 * Mirrors the bean validation on the auth DTOs so the user sees an error before a round trip.
 * Built from a translator, never at module scope — a schema evaluated on import would freeze
 * whichever language loaded first. Callers wrap these in `useMemo` keyed on the translator.
 */
export function buildSignInSchema(t: AuthValidationMessages) {
  return z.object({
    email: z.string().trim().min(1, t("emailRequired")).email(t("emailInvalid")),
    // No length rule here on purpose: this is the sign-in form, and telling somebody their
    // password is "too short" before it is checked is telling them it is the wrong password.
    password: z.string().min(1, t("passwordRequired")),
  });
}

export function buildSignUpSchema(t: AuthValidationMessages) {
  return z.object({
    displayName: z.string().trim().min(1, t("nameRequired")).max(120, t("nameMax")),
    email: z.string().trim().min(1, t("emailRequired")).email(t("emailInvalid")),
    password: z
      .string()
      .min(PASSWORD_MIN, t("passwordMin", { min: PASSWORD_MIN }))
      .max(PASSWORD_MAX, t("passwordMax", { max: PASSWORD_MAX })),
  });
}

export type SignInFormValues = z.infer<ReturnType<typeof buildSignInSchema>>;
export type SignUpFormValues = z.infer<ReturnType<typeof buildSignUpSchema>>;
