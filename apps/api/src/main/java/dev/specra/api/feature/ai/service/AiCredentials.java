package dev.specra.api.feature.ai.service;

import dev.specra.api.feature.workspace.service.AiAccountService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Whether the active provider has anything to authenticate with.
 *
 * <p>This exists so that a missing key is a <em>state</em> the product can report, not a crash. The
 * application boots without one, every non-AI feature keeps working, and a chat request comes back
 * as {@code ai-not-configured} with an actionable sentence instead of a 500 nobody can act on.
 *
 * <p><b>This is the seam for per-workspace keys.</b> Today the answer comes from configuration, so
 * an installation has one key. When workspaces exist, this class resolves the workspace's own
 * credentials first and falls back to the platform's — which is what lets each account bring its
 * own provider, its own model and its own budget. Callers already ask this question rather than
 * reading a property, so that change lands here and nowhere else.
 *
 * <p>No vendor is named: the provider is a value, and the property it lives under is built from
 * that value. A provider that declares no {@code api-key} at all — a locally hosted one, say —
 * needs no credentials and is configured by definition.
 */
@Component
public class AiCredentials {

  /**
   * What {@code application.yml} substitutes when the environment supplies no key. A real empty
   * string would be cleaner, but some provider clients refuse to be built without a value, and the
   * application failing to start is exactly what this class exists to prevent.
   */
  static final String UNSET = "not-set";

  private static final Logger log = LoggerFactory.getLogger(AiCredentials.class);

  private final Environment environment;
  private final AiProviders providers;

  /** Nullable on purpose: unit tests build this class without the workspace feature. */
  @Nullable private final AiAccountService accounts;

  public AiCredentials(
      Environment environment, AiProviders providers, @Nullable AiAccountService accounts) {
    this.environment = environment;
    this.providers = providers;
    this.accounts = accounts;
  }

  /** True when the active chat provider can be called at all. */
  public boolean chatIsConfigured() {
    return isConfigured(providers.chat());
  }

  /**
   * The per-workspace answer: the workspace's own account first — bring-your-own-key — then the
   * platform's configuration. Callers that know which tenant they act for ask this; the ones that
   * predate tenancy keep asking {@link #chatIsConfigured()} and get the platform answer.
   */
  public boolean chatIsConfiguredFor(@Nullable UUID workspaceId) {
    if (workspaceId != null
        && accounts != null
        && accounts.hasUsableKey(workspaceId, providers.chat())) {
      return true;
    }
    return chatIsConfigured();
  }

  public boolean isConfigured(String provider) {
    String key = environment.getProperty("spring.ai." + provider + ".api-key");
    if (key == null) {
      return true;
    }
    return !key.isBlank() && !UNSET.equals(key);
  }

  /**
   * Say it once, at startup, rather than leaving whoever runs the app to discover it from a failed
   * request. A warning and not an error: running without the assistant is a legitimate way to work
   * on everything else.
   */
  @EventListener(ApplicationReadyEvent.class)
  void reportOnStartup() {
    if (chatIsConfigured()) {
      return;
    }
    log.warn(
        "No API key for the '{}' chat provider. The application is running and every other"
            + " feature works; AI endpoints will answer {} until a key is configured.",
        providers.chat(),
        "ai-not-configured");
  }
}
