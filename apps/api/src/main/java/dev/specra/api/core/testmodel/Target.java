package dev.specra.api.core.testmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a step acts on.
 *
 * <p>Normally a <em>reference</em> into the project's page objects — {@code page} plus an optional
 * {@code element} — and only exceptionally a raw {@link Selector}. Keeping the locator on the page
 * object rather than in the step is what makes healing one row instead of an edit in every test
 * that touches the element.
 *
 * <p>The two forms are one record rather than a sealed hierarchy because the JSON has no
 * discriminator and the schema already guarantees exactly one of them is present. {@link #isPage()}
 * and {@link #isSelector()} say which.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Target(String page, String element, Selector selector) {

  @JsonIgnore
  public boolean isPage() {
    return page != null;
  }

  @JsonIgnore
  public boolean isSelector() {
    return selector != null;
  }
}
