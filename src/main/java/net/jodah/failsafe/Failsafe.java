package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

/**
 * Simple, sophisticated failure handling.
 * 
 * @author Jonathan Halterman
 */
public class Failsafe {
  /**
   * Creates and returns a new SyncFailsafe instance that will perform executions and retries synchronously according to
   * the {@code retryPolicy}.
   * 
   * @param <T> result type
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public static <T> SyncFailsafe<T> with(RetryPolicy retryPolicy) {
    return new SyncFailsafe<T>(Assert.notNull(retryPolicy, "retryPolicy"));
  }

  /**
   * Creates and returns a new SyncFailsafe instance that will perform executions and retries synchronously according to
   * the {@code circuitBreaker}.
   * 
   * @param <T> result type
   * @throws NullPointerException if {@code circuitBreaker} is null
   */
  public static <T> SyncFailsafe<T> with(CircuitBreaker circuitBreaker) {
    return new SyncFailsafe<T>(Assert.notNull(circuitBreaker, "circuitBreaker"));
  }
}
