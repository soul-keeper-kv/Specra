"use client";

import { Check, Languages } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { useTransition } from "react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { usePathname, useRouter } from "@/i18n/navigation";
import { LOCALE_LABELS, routing, type Locale } from "@/i18n/routing";
import { cn } from "@/lib/utils";

/**
 * Switches language by re-navigating to the same route under a different prefix.
 *
 * `usePathname` from `@/i18n/navigation` returns the path with the locale already stripped, so
 * `/vi/projects/abc` comes back as `/projects/abc` and can be pushed under the new locale unchanged.
 * Dynamic segments are passed back through `params` because the stripped pathname still contains
 * the placeholder names, not the values.
 */
export function LocaleSwitcher({ className }: { className?: string }) {
  const t = useTranslations("language");
  const active = useLocale() as Locale;
  const pathname = usePathname();
  const params = useParams();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function switchTo(locale: Locale) {
    if (locale === active) return;
    startTransition(() => {
      // @ts-expect-error -- next-intl cannot prove at compile time that the current params match
      // the current pathname; at runtime they always do, because both come from this render.
      router.replace({ pathname, params }, { locale });
    });
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("change")}
          disabled={pending}
          className={cn(className)}
        >
          <Languages className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {routing.locales.map((locale) => (
          <DropdownMenuItem key={locale} onSelect={() => switchTo(locale)}>
            <Check className={cn("size-4", locale === active ? "opacity-100" : "opacity-0")} />
            {LOCALE_LABELS[locale]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
