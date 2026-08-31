"use client";

import { Check } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { useTransition } from "react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { usePathname, useRouter } from "@/i18n/navigation";
import { LOCALE_LABELS, routing, type Locale } from "@/i18n/routing";
import { cn } from "@/lib/utils";

export function LanguageSettings() {
  const t = useTranslations("settings.language");
  const active = useLocale() as Locale;
  const pathname = usePathname();
  const params = useParams();
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("description")}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <fieldset className="grid gap-2">
          <legend className="sr-only">{t("title")}</legend>
          {routing.locales.map((locale) => {
            const selected = locale === active;
            return (
              <button
                key={locale}
                type="button"
                aria-pressed={selected}
                disabled={pending}
                onClick={() =>
                  startTransition(() => {
                    // @ts-expect-error -- next-intl cannot pair the pathname and the params
                    // statically; at runtime they always match, both coming from this render.
                    router.replace({ pathname, params }, { locale });
                  })
                }
                className={cn(
                  "flex items-center justify-between rounded-lg border px-4 py-3 text-left text-sm transition-colors hover:bg-muted disabled:opacity-60",
                  selected && "border-primary ring-2 ring-primary/20",
                )}
              >
                {LOCALE_LABELS[locale]}
                {selected ? <Check className="size-4 text-primary" /> : null}
              </button>
            );
          })}
        </fieldset>
        <p className="text-xs text-muted-foreground">
          {t("current", { language: LOCALE_LABELS[active] })}
        </p>
      </CardContent>
    </Card>
  );
}
