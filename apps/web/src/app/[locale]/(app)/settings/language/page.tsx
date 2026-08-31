import { setRequestLocale } from "next-intl/server";

import { LanguageSettings } from "@/features/settings/components/language-settings";

export default async function LanguageSettingsPage({
  params,
}: PageProps<"/[locale]/settings/language">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <LanguageSettings />;
}
