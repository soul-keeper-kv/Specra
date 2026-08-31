import type messages from "./messages/en.json";

declare global {
  /**
   * Makes every `t("…")` call type-checked against the English bundle.
   *
   * A key that does not exist, or a namespace that has moved, is a compile error rather than a
   * `notes.form.titel` rendered literally in the UI. English is the reference because it is the
   * fallback bundle; the Vitest suite is what proves the other languages match it.
   */
  type IntlMessages = typeof messages;
}
