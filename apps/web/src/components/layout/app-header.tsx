"use client";

import { Search } from "lucide-react";
import { useTranslations } from "next-intl";

import { ProviderBadge } from "@/components/common/provider-badge";
import { Breadcrumbs } from "@/components/layout/breadcrumbs";
import { LocaleSwitcher } from "@/components/i18n/locale-switcher";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useUiStore } from "@/stores/ui-store";

/**
 * Sticky workspace header: where you are on the left, what the app is doing on the right.
 *
 * The search button is a visible affordance for the command palette, which is otherwise only
 * reachable by ⌘K — a shortcut nobody discovers on their own.
 */
export function AppHeader() {
  const t = useTranslations("nav");
  const openCommandPalette = useUiStore((state) => state.openCommandPalette);

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur-sm">
      <SidebarTrigger className="-ml-1" aria-label={t("toggleSidebar")} />
      <Separator orientation="vertical" className="mr-2 h-4" />
      <Breadcrumbs />

      <div className="ml-auto flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={openCommandPalette}
          className="hidden gap-2 text-muted-foreground sm:flex"
        >
          <Search className="size-4" />
          <span className="hidden md:inline">{t("openCommandPalette")}</span>
          <kbd className="ml-2 hidden rounded border bg-muted px-1.5 font-mono text-[10px] md:inline">
            ⌘K
          </kbd>
        </Button>
        <ProviderBadge />
        <LocaleSwitcher />
        <ThemeToggle />
      </div>
    </header>
  );
}
