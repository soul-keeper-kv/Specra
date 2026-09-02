import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { RunDetailView } from "@/features/runs/components/run-detail-view";

export async function generateMetadata(
  props: PageProps<"/[locale]/runs/[runId]">,
): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "runs" });
  return { title: t("detailTitle") };
}

/**
 * A run is addressed by its own id rather than under its project, because a run reference is
 * what people paste into a ticket and the link has to survive without the project in it.
 */
export default async function RunDetailPage({ params }: PageProps<"/[locale]/runs/[runId]">) {
  const { locale, runId } = await params;
  setRequestLocale(locale);

  return <RunDetailView runId={runId} />;
}
