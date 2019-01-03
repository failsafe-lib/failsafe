package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

/**
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeEvent<R> {
  public final R result;
  public final Throwable failure;
  public final ExecutionContext context;

  public FailsafeEvent(R result, Throwable failure, ExecutionContext context) {
    this.result = result;
    this.failure = failure;
    this.context = context;
  }
}
