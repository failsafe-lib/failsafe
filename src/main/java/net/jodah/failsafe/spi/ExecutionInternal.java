package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionContext;

/**
 * Internal execution APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface ExecutionInternal<R> extends ExecutionContext<R> {
  /**
   * Returns the recorded result for an execution attempt.
   */
  ExecutionResult<R> getResult();

  /**
   * Called when execution of the user's supplier is about to begin.
   */
  void preExecute();

  /**
   * Returns whether the execution has been pre-executed, indicating the attempt has started.
   */
  boolean isPreExecuted();

  /**
   * Records an execution attempt which may correspond with an execution result. Async executions will have results
   * recorded separately.
   */
  void recordAttempt();

  /**
   * Records the {@code result} if the execution has been {@link #preExecute() pre-executed} and a result has not
   * already been recorded.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(ExecutionResult<R> result);

  /**
   * Marks the execution as having been cancelled.
   */
  void cancel();

  /**
   * Marks the execution as having been cancelled by the {@code policyExecutor}.
   */
  void cancel(PolicyExecutor<R, ?> policyExecutor);

  /**
   * Returns whether the execution is considered cancelled for the {@code policyExecutor}.
   */
  boolean isCancelled(PolicyExecutor<R, ?> policyExecutor);
}
