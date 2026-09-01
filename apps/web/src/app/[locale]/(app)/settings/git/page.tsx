import { setRequestLocale } from "next-intl/server";

import { GitCredentialSettings } from "@/features/settings/components/git-credential-settings";

export default async function GitSettingsPage({ params }: PageProps<"/[locale]/settings/git">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <GitCredentialSettings />;
}
