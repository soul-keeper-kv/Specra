import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { TestCaseEditorView } from "@/features/testcases/components/testcase-editor-view";

export async function generateMetadata(
  props: PageProps<"/[locale]/projects/[id]/test-cases/[testCaseId]">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "testcases" });
  return { title: t("editor.title") };
}

export default async function TestCaseEditorPage({
  params,
}: PageProps<"/[locale]/projects/[id]/test-cases/[testCaseId]">) {
  const { locale, id, testCaseId } = await params;
  setRequestLocale(locale);

  return <TestCaseEditorView projectId={id} testCaseId={testCaseId} />;
}
