import type { useTranslations } from "next-intl";
import { z } from "zod";

type GitValidationMessages = ReturnType<typeof useTranslations<"git.validation">>;

/**
 * Mirrors the bean validation on `RepositoryRequest`. Built from a translator, never at module
 * scope — a schema evaluated on import freezes whichever language loaded first.
 */
export function buildConnectSchema(t: GitValidationMessages) {
  return z.object({
    provider: z.enum(["GITHUB", "GITLAB", "BITBUCKET"]),
    remoteUrl: z.string().trim().min(1, t("remoteUrlRequired")).max(500, t("remoteUrlMax")),
    defaultBranch: z.string().trim().min(1, t("branchRequired")).max(200, t("branchMax")),
    credentialId: z.string(),
  });
}

export type ConnectFormValues = z.infer<ReturnType<typeof buildConnectSchema>>;

export function buildCredentialSchema(t: GitValidationMessages) {
  return z.object({
    name: z
      .string()
      .trim()
      .min(1, t("credentialNameRequired"))
      .max(120, t("credentialNameMax")),
    username: z.string().trim().max(120, t("credentialUsernameMax")),
    token: z
      .string()
      .trim()
      .min(1, t("credentialTokenRequired"))
      .max(500, t("credentialTokenMax")),
  });
}

export type CredentialFormValues = z.infer<ReturnType<typeof buildCredentialSchema>>;
