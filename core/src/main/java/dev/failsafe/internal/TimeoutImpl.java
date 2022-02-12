package dev.failsafe.internal;

import dev.failsafe.Timeout;
import dev.failsafe.TimeoutBuilder;
import dev.failsafe.TimeoutConfig;
import dev.failsafe.TimeoutExceededException;
import dev.failsafe.spi.PolicyExecutor;

/**
 * A {@link Timeout} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutBuilder
 * @see TimeoutExceededException
 */
public class TimeoutImpl<R> implements Timeout<R> {
  private final TimeoutConfig<R> config;

  public TimeoutImpl(TimeoutConfig<R> config) {
    this.config = config;
  }

  @Override
  public TimeoutConfig<R> getConfig() {
    return config;
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new TimeoutExecutor<>(this, policyIndex);
  }

  @Override
  public String toString() {
    return "Timeout[timeout=" + config.getTimeout() + ", interruptable=" + config.canInterrupt() + ']';
  }
}
