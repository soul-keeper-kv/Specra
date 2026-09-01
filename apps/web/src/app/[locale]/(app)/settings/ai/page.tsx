import { setRequestLocale } from "next-intl/server";

import { AiAccountSettings } from "@/features/settings/components/ai-account-settings";

export default async function AiSettingsPage({ params }: PageProps<"/[locale]/settings/ai">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <AiAccountSettings />;
}
