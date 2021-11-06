package net.jodah.failsafe.internal;

import net.jodah.failsafe.Timeout;
import net.jodah.failsafe.TimeoutBuilder;
import net.jodah.failsafe.TimeoutConfig;
import net.jodah.failsafe.TimeoutExceededException;
import net.jodah.failsafe.spi.AbstractPolicy;
import net.jodah.failsafe.spi.PolicyExecutor;

/**
 * A {@link Timeout} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutBuilder
 * @see TimeoutExceededException
 */
public class TimeoutImpl<R> extends AbstractPolicy<Timeout<R>, R> implements Timeout<R> {
  private final TimeoutConfig config;

  public TimeoutImpl(TimeoutConfig config) {
    this.config = config;
  }

  @Override
  public TimeoutConfig getConfig() {
    return config;
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new TimeoutExecutor<>(this, policyIndex, successHandler, failureHandler);
  }

  @Override
  public String toString() {
    return "Timeout[timeout=" + config.getTimeout() + ", interruptable=" + config.canInterrupt() + ']';
  }
}
