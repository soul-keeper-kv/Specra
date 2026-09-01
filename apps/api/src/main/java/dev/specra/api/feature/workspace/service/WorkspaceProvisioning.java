package dev.specra.api.feature.workspace.service;

import dev.specra.api.core.i18n.MessageResolver;
import dev.specra.api.feature.auth.service.AuthEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives a new account a workspace of its own.
 *
 * <p>This is the arrow the event exists for: auth publishes that somebody registered and knows
 * nothing more; tenancy listens. Without it, auth would have to import this feature, and an
 * installation that only authenticates could not drop the tenancy code.
 *
 * <p>{@code AFTER_COMMIT} and a new transaction, deliberately. Registration is the important half —
 * an account without a workspace is recoverable and a workspace without an account is not — so a
 * failure here is logged and the user still has their account, still signed in. They land on an
 * empty workspaces screen and create one, which is the ordinary path anyway.
 */
@Component
public class WorkspaceProvisioning {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioning.class);

  private final WorkspaceService workspaces;
  private final MessageResolver messages;

  public WorkspaceProvisioning(WorkspaceService workspaces, MessageResolver messages) {
    this.workspaces = workspaces;
    this.messages = messages;
  }

  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onUserRegistered(AuthEvents.UserRegistered event) {
    try {
      // Still the request thread, so LocaleContextHolder holds the language the sign-up form was
      // in — which is the one the workspace should be named in.
      String name = messages.get("workspace.personal.name", event.displayName());
      workspaces.createFor(event.userId(), name);
    } catch (RuntimeException e) {
      log.error("Could not create the first workspace for user {}", event.userId(), e);
    }
  }
}
