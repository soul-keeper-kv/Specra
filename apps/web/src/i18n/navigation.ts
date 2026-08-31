import { createNavigation } from "next-intl/navigation";

import { routing } from "./routing";

/**
 * Locale-aware replacements for `next/link` and `next/navigation`.
 *
 * Import `Link`, `useRouter` and `usePathname` from here rather than from Next directly: these
 * add the `/vi` or `/en` prefix on the way out and strip it on the way in, so a href is written
 * once as `/notes` and `usePathname()` returns `/notes` in both languages.
 */
export const { Link, redirect, usePathname, useRouter, getPathname } =
  createNavigation(routing);
