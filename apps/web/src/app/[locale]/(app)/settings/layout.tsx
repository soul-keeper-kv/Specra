import { getTranslations, setRequestLocale } from "next-intl/server";

import { PageHeader } from "@/components/common/page-header";
import { SettingsNav } from "@/features/settings/components/settings-nav";

/**
 * Settings is a nested layout rather than three sibling pages so the header and the section nav
 * survive navigation between tabs — switching tabs re-renders only the panel.
 */
export default async function SettingsLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("settings");

  return (
    <div className="grid gap-6">
      <PageHeader title={t("title")} description={t("subtitle")} />
      <SettingsNav />
      <div className="max-w-2xl">{children}</div>
    </div>
  );
}
