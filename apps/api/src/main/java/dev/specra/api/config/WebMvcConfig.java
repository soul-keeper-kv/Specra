package dev.specra.api.config;

import dev.specra.api.core.logging.MdcKeys;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the Next.js app — dev server or deployed origin — call this API from the browser. */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

  private final SpecraProperties properties;

  public WebMvcConfig(SpecraProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // Without this the browser hides both ids from JavaScript, and the web app cannot show
        // the user a reference that support can look up.
        .exposedHeaders(MdcKeys.REQUEST_ID_HEADER, MdcKeys.TRACE_ID_HEADER)
        .allowCredentials(true)
        .maxAge(3600);
  }
}
