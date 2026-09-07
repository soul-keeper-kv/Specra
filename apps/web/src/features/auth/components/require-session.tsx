"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

import { SessionWatchdog } from "@/features/auth/components/session-watchdog";
import { useSessionReady, useSessionUser } from "@/features/auth/store";
import { usePathname, useRouter } from "@/i18n/navigation";
import { consumeSessionEndedReason } from "@/lib/api/session";

/**
 * Keeps the signed-in shell signed in.
 *
 * <p>Three states, and the middle one is the reason this component exists. Until storage has been
 * read nothing is known, so it renders a spinner rather than guessing — guessing "signed out" for
 * one frame sends every returning user to the sign-in page and back. Once known, a signed-out
 * visitor is redirected with the page they wanted on {@code ?next=}, so the deep link they followed
 * still resolves after they sign in.
 *
 * <p>It also mounts the {@link SessionWatchdog}, so a session that runs out under an idle tab
 * ends here rather than at the user's next click. When a session ends on its own the reason
 * travels on {@code ?reason=}, which is how the sign-in page knows to explain the arrival instead
 * of presenting a blank form to someone who was working a moment ago.
 *
 * <p>It is a convenience, not the boundary. Nothing here protects data: the API refuses an
 * unauthenticated request whatever this component renders. What it protects is the experience of a
 * screen full of empty error states.
 */
export function RequireSession({ children }: { children: React.ReactNode }) {
  const t = useTranslations("auth.guard");
  const ready = useSessionReady();
  const user = useSessionUser();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const signedOut = ready && !user;

  useEffect(() => {
    if (!signedOut) return;

    // Whatever the previous account loaded must not survive into the next one, and a query left
    // running would only retry against a credential that no longer exists.
    queryClient.clear();

    const reason = consumeSessionEndedReason();
    const params = new URLSearchParams({ next: pathname });
    if (reason) params.set("reason", reason);
    router.replace(`/sign-in?${params.toString()}`);
  }, [signedOut, pathname, router, queryClient]);

  if (!ready || signedOut) {
    return (
      <div className="grid min-h-svh place-items-center" role="status" aria-live="polite">
        <span className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          {t("checking")}
        </span>
      </div>
    );
  }

  return (
    <>
      <SessionWatchdog />
      {children}
    </>
  );
}
