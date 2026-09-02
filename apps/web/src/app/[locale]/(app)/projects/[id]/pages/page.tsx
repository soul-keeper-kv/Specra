import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { PagesView } from "@/features/pages/components/pages-view";
import { ProjectShell } from "@/features/projects/components/project-shell";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/pages">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "pages" });
  return { title: t("title") };
}

export default async function PageObjectsPage({
  params,
}: PageProps<"/[locale]/projects/[id]/pages">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <PagesView projectId={id} />
    </ProjectShell>
  );
}
