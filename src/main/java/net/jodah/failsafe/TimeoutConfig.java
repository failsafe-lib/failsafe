package net.jodah.failsafe;

import java.time.Duration;

/**
 * Configuration for a {@link Timeout}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class TimeoutConfig<R> extends PolicyConfig<R> {
  Duration timeout;
  boolean canInterrupt;

  TimeoutConfig(Duration timeout, boolean canInterrupt) {
    this.timeout = timeout;
    this.canInterrupt = canInterrupt;
  }

  TimeoutConfig(TimeoutConfig<R> config) {
    super(config);
    timeout = config.timeout;
    canInterrupt = config.canInterrupt;
  }

  /**
   * Returns the timeout duration.
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Returns whether the policy can interrupt an execution if the timeout is exceeded.
   *
   * @see TimeoutBuilder#withInterrupt()
   */
  public boolean canInterrupt() {
    return canInterrupt;
  }
}
