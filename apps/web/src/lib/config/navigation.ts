import {
  LayoutDashboard,
  MessagesSquare,
  NotebookPen,
  Settings,
  type LucideIcon,
} from "lucide-react";

/**
 * The workspace sidebar, the command palette and the breadcrumb trail all read this list, so a new
 * page appears in all three at once.
 *
 * `labelKey` is a key in the `nav` message namespace rather than a string: the label has to be
 * translated at render time, and a literal here would be English forever. `href` is written without
 * a locale prefix — the `Link` from `@/i18n/navigation` adds it.
 */
export type NavItem = {
  href: string;
  labelKey: "dashboard" | "notes" | "chat" | "settings";
  icon: LucideIcon;
  /** Match this href only exactly; otherwise a prefix match marks the item active. */
  exact?: boolean;
};

export const WORKSPACE_NAV: readonly NavItem[] = [
  { href: "/dashboard", labelKey: "dashboard", icon: LayoutDashboard },
  { href: "/notes", labelKey: "notes", icon: NotebookPen },
  { href: "/chat", labelKey: "chat", icon: MessagesSquare },
  { href: "/settings", labelKey: "settings", icon: Settings },
];

/** True when `pathname` (already locale-stripped) is inside `item`. */
export function isActive(item: NavItem, pathname: string): boolean {
  return item.exact
    ? pathname === item.href
    : pathname === item.href || pathname.startsWith(`${item.href}/`);
}
