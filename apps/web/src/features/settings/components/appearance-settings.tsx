"use client";

import { Check } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";

import { THEMES } from "@/components/theme/theme-toggle";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Theme picker as three cards rather than a dropdown: on a settings page the point is to show what
 * the options are, not to save space.
 */
export function AppearanceSettings() {
  const t = useTranslations("settings.appearance");
  const tTheme = useTranslations("theme");
  // `theme` is undefined until next-themes has read localStorage, so nothing is marked selected
  // on the first render and the server markup matches.
  const { theme, setTheme } = useTheme();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("title")}</CardTitle>
        <CardDescription>{t("description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <fieldset className="grid gap-3 sm:grid-cols-3">
          <legend className="sr-only">{tTheme("label")}</legend>
          {THEMES.map(({ value, icon: Icon }) => {
            const selected = theme === value;
            return (
              <button
                key={value}
                type="button"
                aria-pressed={selected}
                onClick={() => setTheme(value)}
                className={cn(
                  "flex flex-col items-start gap-2 rounded-lg border p-4 text-left transition-colors hover:bg-muted",
                  selected && "border-primary ring-2 ring-primary/20",
                )}
              >
                <div className="flex w-full items-center justify-between">
                  <Icon className="size-4" />
                  {selected ? <Check className="size-4 text-primary" /> : null}
                </div>
                <span className="text-sm font-medium">{tTheme(value)}</span>
              </button>
            );
          })}
        </fieldset>
      </CardContent>
    </Card>
  );
}
