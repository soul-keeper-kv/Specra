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

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Segments that exist to shape a URL, not to name a place a reader can go. Routing them as crumbs
 * shows an untranslated English word in a Vietnamese trail and links to a path that does not exist
 * on its own.
 */
const STRUCTURAL = new Set(["automation", "test-cases"]);

/** A crumb worth showing: not an id, not a segment that only exists to shape the path. */
function isNoise(segment: string) {
  return UUID.test(segment) || STRUCTURAL.has(segment);
}

/**
 * Derives the trail from the URL rather than from a prop, so no page has to remember to pass one.
 *
 * Only segments the navigation config knows about are translated; anything else — a settings
 * sub-page — is shown as-is, or replaced by `title` when the page supplies one for the leaf.
 *
 * Ids and structural segments are dropped rather than rendered. A UUID says nothing to a reader
 * and, at 36 characters, pushes the rest of the trail off a narrow header; the page heading below
 * already names what it points at.
 */
export function Breadcrumbs({ title }: { title?: string }) {
  const pathname = usePathname();
  const tNav = useTranslations("nav");

  const segments = pathname.split("/").filter(Boolean);
  if (segments.length === 0) return null;

  const crumbs = segments
    .map((segment, index) => {
      const href = `/${segments.slice(0, index + 1).join("/")}`;
      const known = WORKSPACE_NAV.find((item) => item.href === href);
      return { href, label: known ? tNav(known.labelKey) : segment, noise: isNoise(segment) };
    })
    .filter((crumb) => !crumb.noise)
    .map((crumb, index, kept) => ({ ...crumb, last: index === kept.length - 1 }));

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
