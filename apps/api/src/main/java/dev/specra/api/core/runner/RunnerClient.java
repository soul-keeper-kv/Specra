package dev.specra.api.core.runner;

import java.util.Map;

/**
 * The port to the toolchain plane (03-module-boundaries.md).
 *
 * <p>A port rather than a client class for the same reason {@code GitProvider} is one: the
 * transport is a detail, and a feature that depended on the mechanics could not be tested without a
 * socket. Nothing above this interface knows the runner speaks HTTP.
 */
public interface RunnerClient {

  /**
   * Runs one job and returns what the runner made of it.
   *
   * @param kind {@code codegen}, {@code inspect} or {@code execute}
   * @param payload the job's input, serialised as JSON by the implementation
   */
  RunnerJobResult run(String kind, Object payload);

  /** Whether the runner answered its health probe recently enough to be worth calling. */
  boolean isAvailable();

  /**
   * The runner's answer, already separated into "it worked" and "it said no".
   *
   * <p>A refusal is data — an invalid IR, a page nobody inspected — and the feature turns it into
   * something the user can act on. Only a transport failure is an exception.
   *
   * @param result the job's output when {@code ok}; null otherwise
   * @param errorCode the runner's machine-readable reason when it refused
   */
  record RunnerJobResult(boolean ok, Map<String, Object> result, String errorCode, String message) {

    public static RunnerJobResult succeeded(Map<String, Object> result) {
      return new RunnerJobResult(true, result, null, null);
    }

    public static RunnerJobResult refused(String errorCode, String message) {
      return new RunnerJobResult(false, null, errorCode, message);
    }
  }
}
