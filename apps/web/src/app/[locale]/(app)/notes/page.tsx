import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { NotesView } from "@/features/notes/components/notes-view";

export async function generateMetadata(props: PageProps<"/[locale]/notes">): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "notes" });
  return { title: t("title") };
}

export default async function NotesPage({ params }: PageProps<"/[locale]/notes">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return <NotesView />;
}
