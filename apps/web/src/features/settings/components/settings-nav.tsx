"use client";

import { useTranslations } from "next-intl";

import { Link, usePathname } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

const TABS = [
  { href: "/settings", key: "profile" },
  { href: "/settings/appearance", key: "appearance" },
  { href: "/settings/members", key: "members" },
  { href: "/settings/language", key: "language" },
  { href: "/settings/ai", key: "ai" },
  { href: "/settings/git", key: "git" },
] as const;

/**
 * Section navigation for settings. Deliberately real links rather than a Tabs component: each
 * panel has its own URL, so a tab can be linked to and the back button works.
 */
export function SettingsNav() {
  const t = useTranslations("settings.tabs");
  const pathname = usePathname();

  return (
    <nav className="flex flex-wrap gap-1 border-b pb-2">
      {TABS.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "rounded-md px-3 py-1.5 text-sm transition-colors",
              active
                ? "bg-secondary font-medium text-secondary-foreground"
                : "text-muted-foreground hover:bg-secondary/50 hover:text-foreground",
            )}
          >
            {t(tab.key)}
          </Link>
        );
      })}
    </nav>
  );
}
