package dev.specra.api.config;

import dev.specra.api.core.logging.RequestLoggingFilter;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing, metrics and the access log.
 *
 * <p>Micrometer Tracing is on the classpath through the OpenTelemetry bridge, so every HTTP request
 * already gets a span and a {@code traceId}/{@code spanId} pair in the MDC — which is what makes a
 * log line, an error response and a metric describe the same request. No span exporter is wired in:
 * the ids are useful on their own, and pointing them at a collector is one dependency plus {@code
 * management.otlp.tracing.endpoint} when there is a collector to point at.
 *
 * <p>The two aspects below let any method be measured by annotating it, without an explicit timer:
 * {@code @Observed} for a span plus a timer, {@code @Timed} for a timer alone.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

  @Bean
  public ObservedAspect observedAspect(ObservationRegistry registry) {
    return new ObservedAspect(registry);
  }

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  @Bean
  public RequestLoggingFilter requestLoggingFilter(SpecraProperties properties) {
    SpecraProperties.Logging.Access access = properties.logging().access();
    return new RequestLoggingFilter(access.enabled(), access.slowRequestMillis());
  }
}
