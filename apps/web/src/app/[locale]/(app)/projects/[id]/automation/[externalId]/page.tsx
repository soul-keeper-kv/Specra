import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { ProjectShell } from "@/features/projects/components/project-shell";
import { ExternalTestWorkspace } from "@/features/testmanagement/components/external-test-workspace";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/automation/[externalId]">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "testManagement.workspace" });
  return { title: t("title") };
}

export default async function ExternalAutomationPage({
  params,
}: PageProps<"/[locale]/projects/[id]/automation/[externalId]">) {
  const { locale, id, externalId } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <ExternalTestWorkspace projectId={id} externalId={externalId} />
    </ProjectShell>
  );
}
