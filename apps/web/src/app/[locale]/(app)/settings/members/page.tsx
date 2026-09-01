import { getTranslations, setRequestLocale } from "next-intl/server";

import { MembersView } from "@/features/workspaces/components/members-view";

export async function generateMetadata({ params }: PageProps<"/[locale]/settings/members">) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "members" });
  return { title: t("title") };
}

export default async function MembersPage({ params }: PageProps<"/[locale]/settings/members">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <MembersView />;
}
