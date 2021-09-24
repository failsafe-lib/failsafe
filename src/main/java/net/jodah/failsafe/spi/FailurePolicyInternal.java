package net.jodah.failsafe.spi;

import net.jodah.failsafe.FailurePolicy;

/**
 * Internal {@link FailurePolicy} APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface FailurePolicyInternal<R> {
  /**
   * Returns whether an execution result is a failure given a policy's configured failure conditions.
   */
  boolean isFailure(ExecutionResult<R> result);
}