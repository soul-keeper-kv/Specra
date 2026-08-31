import { setRequestLocale } from "next-intl/server";

import { AppHeader } from "@/components/layout/app-header";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { CommandPalette } from "@/components/layout/command-palette";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";

/**
 * The signed-in shell: collapsible sidebar, sticky header, command palette.
 *
 * Mounted once for every workspace route, so navigating between Notes and Chat re-renders only the
 * page — the sidebar keeps its scroll position and its open/collapsed state.
 */
export default async function WorkspaceLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset className="min-w-0">
        <AppHeader />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-8">{children}</main>
      </SidebarInset>
      <CommandPalette />
    </SidebarProvider>
  );
}
