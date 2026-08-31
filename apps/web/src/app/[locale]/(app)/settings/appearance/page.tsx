import { setRequestLocale } from "next-intl/server";

import { AppearanceSettings } from "@/features/settings/components/appearance-settings";

export default async function AppearanceSettingsPage({
  params,
}: PageProps<"/[locale]/settings/appearance">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <AppearanceSettings />;
}
