import { ArrowRight, Cpu, Database, NotebookPen, Radar } from "lucide-react";
import { getTranslations, setRequestLocale } from "next-intl/server";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Link } from "@/i18n/navigation";
import { site } from "@/lib/config/site";

const FEATURES = [
  { key: "notes", icon: NotebookPen },
  { key: "rag", icon: Database },
  { key: "providers", icon: Cpu },
  { key: "observability", icon: Radar },
] as const;

/**
 * The landing page. A Server Component on purpose: it has no interactive state, so it ships no
 * JavaScript beyond the header, and it renders as static HTML for both locales at build time.
 */
export default async function LandingPage({ params }: PageProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  const t = await getTranslations("marketing");

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-16 sm:py-24">
      <section className="grid gap-6 text-center">
        <div className="flex justify-center">
          <Badge variant="outline" className="font-normal">
            {t("badge")}
          </Badge>
        </div>
        <h1 className="font-heading text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          {t("title")}
        </h1>
        <p className="mx-auto max-w-2xl text-pretty text-muted-foreground">{t("subtitle")}</p>
        <div className="flex flex-wrap justify-center gap-3 pt-2">
          <Button asChild>
            <Link href="/dashboard">
              {t("primaryCta")}
              <ArrowRight className="size-4" />
            </Link>
          </Button>
          <Button asChild variant="outline">
            <a href={site.docsUrl} target="_blank" rel="noreferrer">
              {t("secondaryCta")}
            </a>
          </Button>
        </div>
      </section>

      <section className="mt-16 grid gap-4 sm:grid-cols-2">
        {FEATURES.map(({ key, icon: Icon }) => (
          <Card key={key}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Icon className="size-4" />
                {t(`features.${key}.title`)}
              </CardTitle>
              <CardDescription>{t(`features.${key}.body`)}</CardDescription>
            </CardHeader>
            <CardContent />
          </Card>
        ))}
      </section>
    </div>
  );
}
