import { defineRouting } from "next-intl/routing";

/**
 * The one definition of which languages exist and how they appear in the URL.
 *
 * <p>`localePrefix: "always"` keeps the locale in the path for every route, including the default
 * one. That costs a redirect on `/`, and buys three things: a shareable link always carries its
 * language, search engines can index both versions, and there is no "which locale am I in?" state
 * hidden in a cookie that a hard refresh could disagree with.
 */
export const routing = defineRouting({
  locales: ["vi", "en"],
  defaultLocale: "vi",
  localePrefix: "always",
  // Remembers the last choice so a returning visitor landing on `/` is redirected to the
  // language they picked, not to the default one.
  localeCookie: {
    name: "SPECRA_LOCALE",
    maxAge: 60 * 60 * 24 * 365,
  },
});

export type Locale = (typeof routing.locales)[number];

/** What the switcher shows. Each language is named in itself, never translated. */
export const LOCALE_LABELS: Record<Locale, string> = {
  vi: "Tiếng Việt",
  en: "English",
};
