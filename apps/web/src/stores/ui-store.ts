import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Client-only UI state. Anything that lives on the server belongs in TanStack Query
 * instead — keeping the two apart is what stops cache and store drifting out of sync.
 */
type UiState = {
  conversationId: string;
  streaming: boolean;
  ragMode: boolean;
  newConversation: () => void;
  setStreaming: (value: boolean) => void;
  setRagMode: (value: boolean) => void;
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
      newConversation: () => set({ conversationId: newId() }),
      setStreaming: (streaming) => set({ streaming }),
      setRagMode: (ragMode) => set({ ragMode }),
    }),
    {
      name: "specra-ui",
      // Only persist the deliberate preferences, not the transient thread id.
      partialize: (state) => ({ streaming: state.streaming, ragMode: state.ragMode }),
    },
  ),
);
