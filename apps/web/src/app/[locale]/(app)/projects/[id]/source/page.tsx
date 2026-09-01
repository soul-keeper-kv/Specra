import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { SourceControlView } from "@/features/git/components/source-control-view";
import { ProjectShell } from "@/features/projects/components/project-shell";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/source">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "git" });
  return { title: t("title") };
}

export default async function SourceControlPage({
  params,
}: PageProps<"/[locale]/projects/[id]/source">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <SourceControlView projectId={id} />
    </ProjectShell>
  );
}
