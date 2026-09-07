"use client";

import { useEffect } from "react";

import { useSessionSnapshot } from "@/features/auth/store";
import { endSession, isRefreshTokenExpired, millisUntilSessionEnds } from "@/lib/api/session";

/**
 * Signs a tab out when its session runs out, without waiting for the user to click something.
 *
 * <p>The transport already handles the case where a request is made: a spent access token is
 * refreshed before the call, and a refused refresh ends the session. What it cannot handle is the
 * tab nobody is touching — a dashboard left open overnight makes no request, so nothing notices
 * that the refresh token died at 3am, and the user comes back to a screen that looks signed in and
 * behaves signed out. This closes that window by watching the clock instead of the traffic.
 *
 * <p>Two triggers, because one is not enough. The timer covers a tab that stays open and awake.
 * The visibility check covers the one a laptop suspended: timers do not run while a machine
 * sleeps, and a `setTimeout` scheduled for eight hours out fires late rather than on time, so the
 * moment the tab is looked at again the deadline is re-read from the clock.
 *
 * <p>It renders nothing and it is not a security boundary — the API refuses an expired token
 * regardless. What it buys is that the user is told, once, instead of discovering it through a
 * failed action.
 */
export function SessionWatchdog() {
  const { session } = useSessionSnapshot();
  const refreshExpiresAt = session?.refreshExpiresAt;

  useEffect(() => {
    if (!refreshExpiresAt) return;

    const endIfExpired = () => {
      if (isRefreshTokenExpired()) endSession("expired");
    };

    // Already past it — a tab restored from the back/forward cache arrives here.
    endIfExpired();

    const remaining = millisUntilSessionEnds();
    // setTimeout stores its delay in a signed 32-bit int; anything larger wraps and fires
    // immediately, which would sign out every user with a long-lived refresh token. Cap the wait
    // and let the visibility check carry the rest.
    const delay = Math.min(remaining, 2_147_483_000);
    const timer = window.setTimeout(endIfExpired, delay);

    const onVisible = () => {
      if (document.visibilityState === "visible") endIfExpired();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", endIfExpired);

    return () => {
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", endIfExpired);
    };
  }, [refreshExpiresAt]);

  return null;
}
