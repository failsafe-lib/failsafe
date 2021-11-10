package net.jodah.failsafe.internal;

import net.jodah.failsafe.Timeout;
import net.jodah.failsafe.TimeoutBuilder;
import net.jodah.failsafe.TimeoutConfig;
import net.jodah.failsafe.TimeoutExceededException;
import net.jodah.failsafe.spi.PolicyExecutor;

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
