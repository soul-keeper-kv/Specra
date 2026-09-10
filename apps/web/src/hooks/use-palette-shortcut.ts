"use client";

import { useEffect } from "react";

/**
 * Whether a keydown is the command palette's ⌘K / Ctrl-K shortcut.
 *
 * `key` is optional on the event interface and really does arrive undefined — browser
 * extensions and password managers dispatch synthetic keydowns without one — so it is read
 * defensively. Dereferencing it threw on every keystroke and took the page down with it.
 */
export function isPaletteShortcut(event: KeyboardEvent) {
  return event.key?.toLowerCase() === "k" && (event.metaKey || event.ctrlKey);
}

/**
 * Calls `onTrigger` when the palette shortcut is pressed anywhere in the document, and suppresses
 * the browser's own "search page" binding so only one of the two fires.
 */
export function usePaletteShortcut(onTrigger: () => void) {
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (!isPaletteShortcut(event)) return;
      event.preventDefault();
      onTrigger();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onTrigger]);
}
