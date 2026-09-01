package dev.specra.api.core.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Decides which language a request is answered in.
 *
 * <p>{@code Accept-Language} decides it, then {@link SupportedLocale#DEFAULT}. There is no {@code
 * ?lang=} escape hatch: which language a response is written in is content negotiation, so it
 * belongs in a header — as a query parameter it would also give one resource two URLs and split it
 * across caches. Swagger UI offers the header on every operation, and the curl equivalent is {@code
 * -H 'Accept-Language: vi'}.
 *
 * <p>Whatever comes in is narrowed to a locale we actually have a bundle for, so an unsupported
 * language degrades to English rather than to untranslated keys.
 */
public class HttpLocaleResolver extends AcceptHeaderLocaleResolver {

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
    return SupportedLocale.from(request.getLocale());
  }
}
