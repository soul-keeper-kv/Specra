"use client";

import { ArrowRight, Building2, Cpu, FolderKanban, MessagesSquare } from "lucide-react";
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
import { useProjects } from "@/features/projects/api/projects";
import { useActiveWorkspace } from "@/features/workspaces/api/workspaces";
import { Link } from "@/i18n/navigation";

/** Overview of the workspace. A client component because every number on it is a live query. */
export function DashboardView() {
  const t = useTranslations("dashboard");
  const active = useActiveWorkspace();
  // One row is enough: the tile only reads totalElements.
  const projects = useProjects(active.workspace?.id, { size: 1 });
  const providers = useProviders();

  const projectCount = active.isPending
    ? undefined
    : active.workspace === null
      ? "0"
      : projects.isPending
        ? undefined
        : String(projects.data?.totalElements ?? 0);

  // Five cards would stretch absurdly wide on a large monitor with nothing to fill them.
  return (
    <div className="grid max-w-6xl gap-8">
      <PageHeader title={t("title")} description={t("subtitle")} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat
          icon={Building2}
          label={t("stats.workspace")}
          value={active.isPending ? undefined : (active.workspace?.name ?? "—")}
          hint={t("stats.workspaceHint")}
        />
        <Stat
          icon={FolderKanban}
          label={t("stats.projects")}
          value={projectCount}
          hint={t("stats.projectsHint")}
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
          icon={FolderKanban}
          title={t("cards.projects.title")}
          body={t("cards.projects.body")}
          cta={t("cards.projects.cta")}
          href="/projects"
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
          <div className="truncate font-heading text-2xl font-semibold tabular-nums">
            {value}
          </div>
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
