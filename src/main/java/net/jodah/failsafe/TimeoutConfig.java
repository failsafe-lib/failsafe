package net.jodah.failsafe;

import java.time.Duration;

/**
 * Configuration for a {@link Timeout}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @author Jonathan Halterman
 */
public class TimeoutConfig {
  Duration timeout;
  boolean canInterrupt;

  TimeoutConfig(Duration timeout, boolean canInterrupt) {
    this.timeout = timeout;
    this.canInterrupt = canInterrupt;
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
