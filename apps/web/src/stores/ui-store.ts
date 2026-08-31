import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Client-only UI state. Anything that lives on the server belongs in TanStack Query
 * instead — keeping the two apart is what stops cache and store drifting out of sync.
 */
type UiState = {
  /** Which chat thread the API should append to. Not persisted: a reload starts fresh. */
  conversationId: string;
  streaming: boolean;
  ragMode: boolean;
  commandPaletteOpen: boolean;
  newConversation: () => void;
  setStreaming: (value: boolean) => void;
  setRagMode: (value: boolean) => void;
  openCommandPalette: () => void;
  setCommandPaletteOpen: (value: boolean) => void;
};

const newId = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `conv-${Date.now()}`;

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      conversationId: "default",
      streaming: true,
      ragMode: false,
      commandPaletteOpen: false,
      newConversation: () => set({ conversationId: newId() }),
      setStreaming: (streaming) => set({ streaming }),
      setRagMode: (ragMode) => set({ ragMode }),
      openCommandPalette: () => set({ commandPaletteOpen: true }),
      setCommandPaletteOpen: (commandPaletteOpen) => set({ commandPaletteOpen }),
    }),
    {
      name: "specra-ui",
      // Only the deliberate preferences. The thread id is transient, and a palette that
      // reopened itself on every visit would be a bug rather than a restored preference.
      partialize: (state) => ({ streaming: state.streaming, ragMode: state.ragMode }),
    },
  ),
);
