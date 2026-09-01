import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { ProjectShell } from "@/features/projects/components/project-shell";
import { TestManagementView } from "@/features/testmanagement/components/test-management-view";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/integrations">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "testManagement" });
  return { title: t("title") };
}

export default async function IntegrationsPage({
  params,
}: PageProps<"/[locale]/projects/[id]/integrations">) {
  const { locale, id } = await params;
  setRequestLocale(locale);
  return (
    <ProjectShell projectId={id}>
      <TestManagementView projectId={id} />
    </ProjectShell>
  );
}
