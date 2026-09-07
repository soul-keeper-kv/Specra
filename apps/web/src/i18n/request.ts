import { hasLocale } from "next-intl";
import { getRequestConfig } from "next-intl/server";

import { routing } from "./routing";

/**
 * Loads the message bundle for the request's locale. Registered through the next-intl plugin in
 * next.config.ts, which is why nothing imports this file directly.
 */
export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  // A locale from the URL is user input: narrow it to one we actually ship before it reaches
  // a dynamic import.
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
    // No `timeZone` on purpose: a timestamp renders in the timezone of whoever is reading it.
    // The API sends every instant as ISO-8601 UTC, so the conversion belongs at the last step.
    //
    // This works because dates are only ever formatted inside client components — see the
    // rule in the specra-web skill. Formatting one in a server component would render it in
    // the *server's* timezone and hydrate into the reader's, which is a mismatch; format it
    // in a client component, or pass the raw ISO string down to one.
  };
});
