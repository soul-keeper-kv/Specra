import * as React from "react";

const MOBILE_BREAKPOINT = 768;

/**
 * True below the `sm`/`md` boundary the sidebar switches on.
 *
 * Written with `useSyncExternalStore` rather than the effect-plus-setState shape shadcn ships:
 * the media query is external state, so subscribing to it directly avoids the extra render on
 * mount that the React Compiler lint rule flags. The server snapshot is `false`, which keeps
 * server and client markup identical until the real width is known.
 */
export function useIsMobile() {
  const subscribe = React.useCallback((onStoreChange: () => void) => {
    const query = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT - 1}px)`);
    query.addEventListener("change", onStoreChange);
    return () => query.removeEventListener("change", onStoreChange);
  }, []);

  return React.useSyncExternalStore(
    subscribe,
    () => window.innerWidth < MOBILE_BREAKPOINT,
    () => false,
  );
}
