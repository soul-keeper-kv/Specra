package dev.specra.api.feature.pageobject.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;
import java.util.Map;

/**
 * The browser never reached the page that was asked for.
 *
 * <p>Refusing is the whole point. Inspection used to store whatever the browser ended up on, so an
 * application that bounces an anonymous visitor to its sign-in screen produced a {@code
 * DashboardPage} holding {@code emailInput} and {@code passwordInput} — no error, no warning, and
 * everything downstream believing it. A page object nobody can tell is wrong is worse than no page
 * object at all, which is what invariant 5 is about.
 *
 * <p>Both URLs travel with the problem rather than only in the sentence: the UI shows where the
 * inspection landed, and the fix — give the inspection a way to sign in — follows from seeing it.
 */
public class InspectionRedirectedException extends BusinessException {

  private final String requested;
  private final String reached;

  public InspectionRedirectedException(String requested, String reached) {
    super(ErrorCode.INSPECTION_REDIRECTED, ErrorCode.INSPECTION_REDIRECTED.detailKey(), reached);
    this.requested = requested;
    this.reached = reached;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of("requested", requested, "reached", reached);
  }
}
