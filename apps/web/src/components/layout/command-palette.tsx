"use client";

import { Languages, Monitor, Moon, Sun } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { useParams } from "next/navigation";
import { useCallback } from "react";

import {
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { usePaletteShortcut } from "@/hooks/use-palette-shortcut";
import { usePathname, useRouter } from "@/i18n/navigation";
import { LOCALE_LABELS, routing, type Locale } from "@/i18n/routing";
import { WORKSPACE_NAV } from "@/lib/config/navigation";
import { useUiStore } from "@/stores/ui-store";

const THEME_ICONS = { light: Sun, dark: Moon, system: Monitor } as const;

/**
 * ⌘K / Ctrl-K palette: navigation, theme and language in one place.
 *
 * Mounted once in the workspace layout and driven from the UI store, so the header button and the
 * keyboard shortcut open the same instance rather than two competing dialogs.
 */
export function CommandPalette() {
  const t = useTranslations("commandPalette");
  const tNav = useTranslations("nav");
  const tTheme = useTranslations("theme");
  const open = useUiStore((state) => state.commandPaletteOpen);
  const setOpen = useUiStore((state) => state.setCommandPaletteOpen);

  const router = useRouter();
  const pathname = usePathname();
  const params = useParams();
  const { setTheme } = useTheme();
  const activeLocale = useLocale() as Locale;

  const toggle = useCallback(
    () => setOpen(!useUiStore.getState().commandPaletteOpen),
    [setOpen],
  );
  usePaletteShortcut(toggle);

  /** Every item closes the dialog first, so the route change is not animated behind an overlay. */
  function run(action: () => void) {
    setOpen(false);
    action();
  }

  return (
    <CommandDialog
      open={open}
      onOpenChange={setOpen}
      title={t("open")}
      description={t("placeholder")}
    >
      {/*
        This CommandDialog only supplies the dialog; unlike the plain shadcn variant it does not
        wrap its children in <Command>. Without this wrapper every CommandInput/Item below reads
        an undefined cmdk store and the whole page throws on render.
      */}
      <Command>
        <CommandInput placeholder={t("placeholder")} />
        <CommandList>
          <CommandEmpty>{t("empty")}</CommandEmpty>

          <CommandGroup heading={t("groups.navigation")}>
            {WORKSPACE_NAV.map((item) => (
              <CommandItem
                key={item.href}
                value={`${tNav(item.labelKey)} ${item.href}`}
                onSelect={() => run(() => router.push(item.href))}
              >
                <item.icon className="size-4" />
                {tNav(item.labelKey)}
              </CommandItem>
            ))}
          </CommandGroup>

          <CommandSeparator />

          <CommandGroup heading={t("groups.theme")}>
            {(["light", "dark", "system"] as const).map((value) => {
              const Icon = THEME_ICONS[value];
              return (
                <CommandItem
                  key={value}
                  value={`${tTheme("label")} ${tTheme(value)}`}
                  onSelect={() => run(() => setTheme(value))}
                >
                  <Icon className="size-4" />
                  {tTheme(value)}
                </CommandItem>
              );
            })}
          </CommandGroup>

          <CommandSeparator />

          <CommandGroup heading={t("groups.language")}>
            {routing.locales
              .filter((locale) => locale !== activeLocale)
              .map((locale) => (
                <CommandItem
                  key={locale}
                  value={LOCALE_LABELS[locale]}
                  onSelect={() =>
                    run(() =>
                      // @ts-expect-error -- next-intl cannot pair `pathname` and `params` statically;
                      // at runtime they always match, because both come from this render.
                      router.replace({ pathname, params }, { locale }),
                    )
                  }
                >
                  <Languages className="size-4" />
                  {LOCALE_LABELS[locale]}
                </CommandItem>
              ))}
          </CommandGroup>
        </CommandList>
      </Command>
    </CommandDialog>
  );
}
