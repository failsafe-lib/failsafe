package net.jodah.failsafe.spi;

import net.jodah.failsafe.AsyncExecution;

/**
 * Internal async execution APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface AsyncExecutionInternal<R> extends ExecutionInternal<R>, AsyncExecution<R> {
  /**
   * Returns whether the execution is an async integration execution.
   */
  boolean isAsyncExecution();

  /**
   * Returns whether one of the public {@link AsyncExecution} record or complete methods have been called.
   */
  boolean isRecorded();

  /**
   * Sets the PolicyExecutor corresponding to the {@code policyIndex} as having post-executed.
   */
  void setPostExecuted(int policyIndex);

  /**
   * Returns whether the PolicyExecutor corresponding to the {@code policyIndex} has already post-executed.
   */
  boolean isPostExecuted(int policyIndex);

  /**
   * Returns a new copy of the AsyncExecutionInternal.
   */
  AsyncExecutionInternal<R> copy();
}
