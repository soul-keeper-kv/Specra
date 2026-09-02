import { setRequestLocale } from "next-intl/server";

import { SecuritySettings } from "@/features/settings/components/security-settings";

export default async function SecuritySettingsPage({
  params,
}: PageProps<"/[locale]/settings/security">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <SecuritySettings />;
}
