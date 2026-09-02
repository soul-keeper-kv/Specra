import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { ProjectShell } from "@/features/projects/components/project-shell";
import { RunsView } from "@/features/runs/components/runs-view";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/runs">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "runs" });
  return { title: t("title") };
}

export default async function RunsPage({ params }: PageProps<"/[locale]/projects/[id]/runs">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <RunsView projectId={id} />
    </ProjectShell>
  );
}
