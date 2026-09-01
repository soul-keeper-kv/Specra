package dev.specra.api.feature.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The outcome of one probe of the active chat provider.
 *
 * <p>Four values rather than a boolean, because the three ways of not being usable want three
 * different answers from the UI: "add a key", "the provider is broken, here is the code", and "no
 * probe has run yet". A client branches on this exactly as it branches on {@code code} elsewhere —
 * the names are the contract, and nothing here is shown to a user untranslated.
 */
@Schema(description = "Result of the last probe of the chat provider")
public enum AiHealthStatus {
  /** The provider answered a probe prompt. */
  UP,
  /** The probe was sent and failed, or timed out. {@code code} says which. */
  DOWN,
  /**
   * No usable credentials for the active provider, so nothing was sent. Actionable by the person
   * running the installation, which is why it is not simply {@link #DOWN}.
   */
  NOT_CONFIGURED,
  /** No probe has completed yet — the app has just started, or checks are switched off. */
  UNKNOWN
}
