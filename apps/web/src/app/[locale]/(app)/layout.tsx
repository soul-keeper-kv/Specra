import { setRequestLocale } from "next-intl/server";

import { AppHeader } from "@/components/layout/app-header";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { CommandPalette } from "@/components/layout/command-palette";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { RequireSession } from "@/features/auth/components/require-session";

/**
 * The signed-in shell: collapsible sidebar, sticky header, command palette.
 *
 * Mounted once for every workspace route, so navigating between Notes and Chat re-renders only the
 * page — the sidebar keeps its scroll position and its open/collapsed state.
 *
 * The main column is capped only at 120rem, wide enough that the three-pane automation workspace
 * gets a real centre stage on a normal monitor. Pages that read better narrow — settings, the test
 * case editor — set their own max width instead of every page paying for theirs.
 */
export default async function WorkspaceLayout({ children, params }: LayoutProps<"/[locale]">) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <RequireSession>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset className="min-w-0">
          <AppHeader />
          <main className="mx-auto w-full max-w-[120rem] flex-1 px-4 py-6 sm:px-6 sm:py-8 xl:px-8">
            {children}
          </main>
        </SidebarInset>
        <CommandPalette />
      </SidebarProvider>
    </RequireSession>
  );
}
