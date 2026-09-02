import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { EnvironmentsView } from "@/features/environments/components/environments-view";
import { ProjectShell } from "@/features/projects/components/project-shell";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/environments">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "environments" });
  return { title: t("title") };
}

export default async function EnvironmentsPage({
  params,
}: PageProps<"/[locale]/projects/[id]/environments">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <EnvironmentsView projectId={id} />
    </ProjectShell>
  );
}
