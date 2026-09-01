package dev.specra.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} for the whole application.
 *
 * <p>Boot auto-configures a {@code ThreadPoolTaskScheduler} for it, so nothing else is needed here.
 * The pool is small on purpose: a scheduled job that can block — a network call, a model call —
 * must not run on it directly, or one hung task stops every other job in the application. {@code
 * AiHealthService} shows the shape: the timer decides <em>when</em>, the work happens on its own
 * executor under its own timeout.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
