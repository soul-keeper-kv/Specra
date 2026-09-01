package dev.specra.api.config;

import dev.specra.api.core.i18n.HttpLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Wires the message bundle into the two places that produce user-facing text.
 *
 * <p>The bundle itself is configured in {@code application.yml} ({@code spring.messages.basename}).
 * What has to be done in code is the pair below: choosing the locale per request, and pointing Bean
 * Validation at the same bundle so {@code @NotBlank(message =
 * "{validation.testcase.title.required}")} resolves from {@code messages_vi.properties} instead of
 * Hibernate Validator's English defaults.
 */
@Configuration(proxyBeanMethods = false)
public class I18nConfig {

  /**
   * Must be named {@code localeResolver}: that is the bean name {@code DispatcherServlet} looks up,
   * and the name Boot's own auto-configuration backs off from.
   */
  @Bean
  public LocaleResolver localeResolver() {
    return new HttpLocaleResolver();
  }

  /**
   * Replaces Boot's default validator so constraint messages are interpolated against our {@link
   * MessageSource}. Hibernate Validator's built-in bundles stay in place as a fallback, so an
   * annotation without an explicit key still produces a sentence rather than a bare {@code {key}}.
   *
   * <p>The bean name matters: {@code defaultValidator} is what {@code ValidationAutoConfiguration}
   * declares, so this one replaces it instead of sitting alongside it.
   */
  @Bean
  public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    return validator;
  }
}
