"use client";

import { useTranslations } from "next-intl";
import { Fragment } from "react";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Link, usePathname } from "@/i18n/navigation";
import { WORKSPACE_NAV } from "@/lib/config/navigation";

/**
 * Derives the trail from the URL rather than from a prop, so no page has to remember to pass one.
 *
 * Only segments the navigation config knows about are translated; anything else — a note id, a
 * settings sub-page — is shown as-is or, for the leaf, replaced by `title` when the page supplies
 * one. That keeps a UUID from ever being rendered as a crumb label.
 */
export function Breadcrumbs({ title }: { title?: string }) {
  const pathname = usePathname();
  const tNav = useTranslations("nav");

  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return null;

  const crumbs = segments.map((segment, index) => {
    const href = `/${segments.slice(0, index + 1).join("/")}`;
    const known = WORKSPACE_NAV.find((item) => item.href === href);
    return {
      href,
      label: known ? tNav(known.labelKey) : segment,
      last: index === segments.length - 1,
    };
  });

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map((crumb) => (
          <Fragment key={crumb.href}>
            <BreadcrumbItem>
              {crumb.last ? (
                <BreadcrumbPage className="max-w-[16rem] truncate">
                  {title ?? crumb.label}
                </BreadcrumbPage>
              ) : (
                <BreadcrumbLink asChild>
                  <Link href={crumb.href}>{crumb.label}</Link>
                </BreadcrumbLink>
              )}
            </BreadcrumbItem>
            {crumb.last ? null : <BreadcrumbSeparator />}
          </Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
