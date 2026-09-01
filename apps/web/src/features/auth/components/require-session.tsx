"use client";

import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

import { useSessionReady, useSessionUser } from "@/features/auth/store";
import { usePathname, useRouter } from "@/i18n/navigation";

/**
 * Keeps the signed-in shell signed in.
 *
 * <p>Three states, and the middle one is the reason this component exists. Until storage has been
 * read nothing is known, so it renders a spinner rather than guessing — guessing "signed out" for
 * one frame sends every returning user to the sign-in page and back. Once known, a signed-out
 * visitor is redirected with the page they wanted on {@code ?next=}, so the deep link they followed
 * still resolves after they sign in.
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

  const signedOut = ready && !user;

  useEffect(() => {
    if (signedOut) {
      router.replace(`/sign-in?next=${encodeURIComponent(pathname)}`);
    }
  }, [signedOut, pathname, router]);

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

  return <>{children}</>;
}
