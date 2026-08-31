import createMiddleware from "next-intl/middleware";

import { routing } from "@/i18n/routing";

/**
 * Locale routing, at the edge.
 *
 * Redirects `/` to the visitor's language, rejects a prefix that is not a configured locale, and
 * sets the locale cookie so a return visit lands where they left off.
 *
 * Named `proxy.ts`, not `middleware.ts`: Next 16 renamed the convention and warns on the old name.
 * next-intl still calls its factory `createMiddleware` — only the file and the export are new.
 */
export default createMiddleware(routing);

export const config = {
  // Everything except Next internals, the API proxy path and anything with a file extension.
  // Without the last exclusion this would try to give favicon.ico a locale prefix.
  matcher: ["/", "/(vi|en)/:path*", "/((?!_next|_vercel|api|.*\..*).*)"],
};
