package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

/**
 * Safety from synchronous and asynchronous execution failures.
 * 
 * @author Jonathan Halterman
 */
public class Failsafe {
  /**
   * Creates and returns a new SyncFailsafe instance that will perform executions and retries synchronously according to
   * the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public static SyncFailsafe with(RetryPolicy retryPolicy) {
    return new SyncFailsafe(Assert.notNull(retryPolicy, "retryPolicy"));
  }

  /**
   * Creates and returns a new SyncFailsafe instance that will perform executions and retries synchronously according to
   * the {@code circuitBreaker}.
   * 
   * @throws NullPointerException if {@code circuitBreaker} is null
   */
  public static SyncFailsafe with(CircuitBreaker circuitBreaker) {
    return new SyncFailsafe(Assert.notNull(circuitBreaker, "circuitBreaker"));
  }
}
