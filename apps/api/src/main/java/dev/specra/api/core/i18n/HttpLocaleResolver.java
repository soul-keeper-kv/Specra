package dev.specra.api.core.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Decides which language a request is answered in.
 *
 * <p>{@code ?lang=vi} wins, then {@code Accept-Language}, then {@link SupportedLocale#DEFAULT}. The
 * query parameter exists because it makes the Swagger UI and a plain {@code curl} able to see the
 * translated responses without editing headers — the web app always sends the header.
 *
 * <p>Whatever comes in is narrowed to a locale we actually have a bundle for, so an unsupported
 * language degrades to English rather than to untranslated keys.
 */
public class HttpLocaleResolver extends AcceptHeaderLocaleResolver {

  public static final String LANG_PARAM = "lang";

  public HttpLocaleResolver() {
    setSupportedLocales(SupportedLocale.locales());
    setDefaultLocale(SupportedLocale.DEFAULT.locale());
  }

  @Override
  @NonNull public Locale resolveLocale(@NonNull HttpServletRequest request) {
    return resolve(request).locale();
  }

  /**
   * Static so the logging filter can tag a line with the same locale the controller will answer in.
   * Filters run before the {@code DispatcherServlet} populates {@code LocaleContextHolder}, so they
   * cannot simply read it back.
   */
  public static SupportedLocale resolve(HttpServletRequest request) {
    String requested = request.getParameter(LANG_PARAM);
    if (StringUtils.hasText(requested)) {
      return SupportedLocale.from(Locale.forLanguageTag(requested));
    }
    return SupportedLocale.from(request.getLocale());
  }
}
