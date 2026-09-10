import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { isPaletteShortcut, usePaletteShortcut } from "./use-palette-shortcut";

/** The parts of a real keydown the predicate reads; the rest never gets touched. */
function keydown(init: Partial<KeyboardEvent>) {
  return init as KeyboardEvent;
}

describe("isPaletteShortcut", () => {
  it("matches ⌘K and Ctrl-K, in either case", () => {
    expect(isPaletteShortcut(keydown({ key: "k", metaKey: true }))).toBe(true);
    expect(isPaletteShortcut(keydown({ key: "k", ctrlKey: true }))).toBe(true);
    expect(isPaletteShortcut(keydown({ key: "K", ctrlKey: true }))).toBe(true);
  });

  it("ignores K without a modifier, and other modified keys", () => {
    expect(isPaletteShortcut(keydown({ key: "k" }))).toBeFalsy();
    expect(isPaletteShortcut(keydown({ key: "j", metaKey: true }))).toBe(false);
  });

  it("survives an event that carries no key at all", () => {
    // Extensions and password managers dispatch synthetic keydowns without one. Dereferencing
    // `key` threw on every keystroke and took the page down with it.
    const synthetic = keydown({ metaKey: true });
    expect(() => isPaletteShortcut(synthetic)).not.toThrow();
    expect(isPaletteShortcut(synthetic)).toBeFalsy();
  });
});

describe("usePaletteShortcut", () => {
  it("fires on the shortcut and suppresses the browser's own binding", () => {
    const onTrigger = vi.fn();
    renderHook(() => usePaletteShortcut(onTrigger));

    const event = new KeyboardEvent("keydown", { key: "k", ctrlKey: true, cancelable: true });
    document.dispatchEvent(event);

    expect(onTrigger).toHaveBeenCalledOnce();
    expect(event.defaultPrevented).toBe(true);
  });

  it("does not fire for an unrelated key, and stops listening once unmounted", () => {
    const onTrigger = vi.fn();
    const { unmount } = renderHook(() => usePaletteShortcut(onTrigger));

    document.dispatchEvent(new KeyboardEvent("keydown", { key: "j", ctrlKey: true }));
    expect(onTrigger).not.toHaveBeenCalled();

    unmount();
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "k", ctrlKey: true }));
    expect(onTrigger).not.toHaveBeenCalled();
  });
});
