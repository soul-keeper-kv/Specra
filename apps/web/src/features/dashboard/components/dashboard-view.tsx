"use client";

import { ArrowRight, Cpu, Database, MessagesSquare, NotebookPen } from "lucide-react";
import { useTranslations } from "next-intl";
import type { LucideIcon } from "lucide-react";

import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useProviders } from "@/features/chat/api/ai";
import { useNotes } from "@/features/notes/api/notes";
import { Link } from "@/i18n/navigation";

/** Overview of the workspace. A client component because every number on it is a live query. */
export function DashboardView() {
  const t = useTranslations("dashboard");
  const notes = useNotes({ size: 5 });
  const providers = useProviders();

  const indexed = notes.data?.content.filter((note) => note.indexedAt).length ?? 0;

  return (
    <div className="grid gap-8">
      <PageHeader title={t("title")} description={t("subtitle")} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat
          icon={NotebookPen}
          label={t("stats.notes")}
          value={notes.isPending ? undefined : String(notes.data?.totalElements ?? 0)}
          hint={t("stats.notesHint")}
        />
        <Stat
          icon={Database}
          label={t("stats.indexed")}
          value={notes.isPending ? undefined : String(indexed)}
          hint={t("stats.indexedHint")}
        />
        <Stat
          icon={Cpu}
          label={t("stats.provider")}
          value={providers.isPending ? undefined : (providers.data?.chatProvider ?? "—")}
          hint={
            providers.data
              ? t("stats.providerHint", {
                  provider: providers.data.embeddingProvider,
                  dimensions: providers.data.embeddingDimensions,
                })
              : t("stats.providerFallback")
          }
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <ShortcutCard
          icon={NotebookPen}
          title={t("cards.notes.title")}
          body={t("cards.notes.body")}
          cta={t("cards.notes.cta")}
          href="/notes"
        />
        <ShortcutCard
          icon={MessagesSquare}
          title={t("cards.chat.title")}
          body={t("cards.chat.body")}
          cta={t("cards.chat.cta")}
          href="/chat"
        />
      </div>

      {providers.data ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <span>{t("swapHint")}</span>
          {providers.data.availableProviders.map((provider) => (
            <Badge key={provider} variant="outline" className="font-mono font-normal">
              AI_CHAT_PROVIDER={provider}
            </Badge>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: LucideIcon;
  label: string;
  /** Undefined means "still loading" — the skeleton keeps the card from resizing when it arrives. */
  value?: string;
  hint: string;
}) {
  return (
    <Card>
      <CardContent className="grid gap-1">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Icon className="size-4" />
          {label}
        </div>
        {value === undefined ? (
          <Skeleton className="h-8 w-20" />
        ) : (
          <div className="font-heading text-2xl font-semibold tabular-nums">{value}</div>
        )}
        <p className="text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  );
}

function ShortcutCard({
  icon: Icon,
  title,
  body,
  cta,
  href,
}: {
  icon: LucideIcon;
  title: string;
  body: string;
  cta: string;
  href: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className="size-4" />
          {title}
        </CardTitle>
        <CardDescription>{body}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild variant="outline" size="sm">
          <Link href={href}>
            {cta}
            <ArrowRight className="size-4" />
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
