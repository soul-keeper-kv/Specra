import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { ProjectShell } from "@/features/projects/components/project-shell";
import { TestCasesView } from "@/features/testcases/components/testcases-view";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "testcases" });
  return { title: t("title") };
}

export default async function ProjectPage({ params }: PageProps<"/[locale]/projects/[id]">) {
  const { locale, id } = await params;
  setRequestLocale(locale);

  return (
    <ProjectShell projectId={id}>
      <TestCasesView projectId={id} />
    </ProjectShell>
  );
}
