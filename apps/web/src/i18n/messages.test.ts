import { describe, expect, it } from "vitest";

import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { LOCALE_LABELS, routing } from "@/i18n/routing";

const BUNDLES: Record<string, unknown> = { en, vi };

/**
 * The web-side counterpart to the API's MessageBundleTest.
 *
 * Missing translations are the classic way i18n rots: a key is added to one bundle, nobody adds it
 * to the other, and next-intl renders the raw key path — but only for users in that language, on
 * that screen. These run in milliseconds and catch it at build time instead.
 */
describe("message bundles", () => {
  it("ships a bundle for every configured locale", () => {
    for (const locale of routing.locales) {
      expect(BUNDLES[locale], `no messages/${locale}.json`).toBeDefined();
      expect(LOCALE_LABELS[locale], `no label for ${locale}`).toBeTruthy();
    }
  });

  it("keeps every bundle at exactly the keys of the English one", () => {
    const expected = flatten(en);

    for (const locale of routing.locales) {
      const actual = flatten(BUNDLES[locale]);

      expect(
        expected.filter((key) => !actual.includes(key)),
        `keys missing from messages/${locale}.json`,
      ).toEqual([]);
      expect(
        actual.filter((key) => !expected.includes(key)),
        `keys in messages/${locale}.json that no longer exist in English`,
      ).toEqual([]);
    }
  });

  it("keeps the same ICU placeholders in every translation", () => {
    const reference = placeholders(en);

    for (const locale of routing.locales) {
      const translated = placeholders(BUNDLES[locale]);
      for (const [key, names] of Object.entries(reference)) {
        // A translation that drops `{title}` renders a sentence with a hole in it, and one that
        // invents a placeholder throws at render time — both are silent until someone looks.
        expect(translated[key], `placeholders differ in ${locale} at ${key}`).toEqual(names);
      }
    }
  });
});

/** Every leaf path, e.g. "notes.form.title". */
function flatten(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    flatten(child, prefix ? `${prefix}.${key}` : key),
  );
}

/** Leaf path to the sorted set of `{name}` placeholders it contains. */
function placeholders(bundle: unknown): Record<string, string[]> {
  const out: Record<string, string[]> = {};

  const walk = (value: unknown, prefix: string) => {
    if (typeof value === "string") {
      // Matches `{name}` and the argument of `{count, plural, …}` alike, which is all that has
      // to line up between languages — the plural categories themselves differ by design.
      // Requiring `}` or `,` right after the name is what keeps the *body* of a plural branch,
      // `=0 {No notes}`, from being mistaken for an argument called "No".
      const names = [...value.matchAll(/\{\s*([A-Za-z0-9_]+)\s*[,}]/g)].map(
        (match) => match[1],
      );
      out[prefix] = [...new Set(names)].sort();
      return;
    }
    if (typeof value === "object" && value !== null) {
      for (const [key, child] of Object.entries(value)) {
        walk(child, prefix ? `${prefix}.${key}` : key);
      }
    }
  };

  walk(bundle, "");
  return out;
}
