"use client";

import { ClipboardList, GitBranch, Layers, Plug, PlayCircle, Server } from "lucide-react";
import { useTranslations } from "next-intl";

import { Link, usePathname } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

/**
 * The sections of one project. Real links rather than a Tabs component, for the same reason
 * settings uses links: each section has its own URL, so it can be shared and the back button
 * works.
 */
export function ProjectTabs({ projectId }: { projectId: string }) {
  const t = useTranslations("projects.tabs");
  const pathname = usePathname();

  const tabs = [
    { href: `/projects/${projectId}`, key: "testCases", icon: ClipboardList, exact: true },
    { href: `/projects/${projectId}/source`, key: "source", icon: GitBranch, exact: false },
    { href: `/projects/${projectId}/pages`, key: "pages", icon: Layers, exact: false },
    { href: `/projects/${projectId}/runs`, key: "runs", icon: PlayCircle, exact: false },
    {
      href: `/projects/${projectId}/environments`,
      key: "environments",
      icon: Server,
      exact: false,
    },
    {
      href: `/projects/${projectId}/integrations`,
      key: "integrations",
      icon: Plug,
      exact: false,
    },
  ] as const;

  return (
    <nav className="flex flex-wrap gap-1 border-b pb-2">
      {tabs.map((tab) => {
        const active = tab.exact ? pathname === tab.href : pathname.startsWith(tab.href);
        const Icon = tab.icon;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
              active
                ? "bg-muted text-foreground"
                : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
            )}
          >
            <Icon className="size-4" />
            {t(tab.key)}
          </Link>
        );
      })}
    </nav>
  );
}
