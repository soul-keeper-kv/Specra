import type { Metadata } from "next";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Geist, Geist_Mono } from "next/font/google";
import { notFound } from "next/navigation";

import { Providers } from "@/components/providers";
import { routing } from "@/i18n/routing";
import { site } from "@/lib/config/site";

import "@/styles/globals.css";

// The shadcn theme reads --font-sans / --font-geist-mono, so bind them here.
const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

/**
 * This is the root layout. There is no `app/layout.tsx`: every route lives under `[locale]`, and
 * the `<html lang>` attribute cannot be written until the locale is known.
 */
export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata(props: LayoutProps<"/[locale]">): Promise<Metadata> {
  const { locale } = await props.params;
  const t = await getTranslations({ locale, namespace: "app" });

  return {
    title: { default: t("name"), template: `%s · ${t("name")}` },
    description: t("tagline"),
    metadataBase: new URL(site.url),
    // Tells crawlers that the two language versions are the same page, not duplicate content.
    alternates: {
      canonical: `/${locale}`,
      languages: Object.fromEntries(routing.locales.map((it) => [it, `/${it}`])),
    },
  };
}

export default async function LocaleLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;

  // The segment is user input. Anything not in `routing.locales` is a 404, not a fallback —
  // otherwise /fr would silently render English under a French URL.
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }

  // Required for the static rendering of this segment; without it every page under it opts into
  // dynamic rendering the moment it reads a translation.
  setRequestLocale(locale);

  return (
    <html
      lang={locale}
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col bg-background text-foreground">
        {/*
          Messages are passed from the server rather than fetched again on the client, so the
          first paint is already translated. The API client reads this locale back off
          <html lang> to set Accept-Language.
        */}
        <NextIntlClientProvider>
          <Providers>{children}</Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
