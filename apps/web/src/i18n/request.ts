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
    // Dates and numbers render in the site's timezone rather than the server's, so server and
    // client markup agree and hydration does not warn.
    timeZone: "Asia/Ho_Chi_Minh",
  };
});
