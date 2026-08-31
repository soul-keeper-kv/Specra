"use client";

import { useEffect, useState } from "react";

/**
 * Trails `value` by `delayMs`, so a search box turns a burst of keystrokes into one request.
 * Returns the input unchanged on the first render, which keeps the initial query immediate.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
