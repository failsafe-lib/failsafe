package net.jodah.failsafe;

import java.util.concurrent.CompletableFuture;

/**
 * Allows asynchronous executions to record their results or complete an execution.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface AsyncExecution<R> extends ExecutionContext<R> {
  /**
   * Completes the execution and the associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if the execution is already recorded or complete
   */
  void complete();

  /**
   * Returns whether the execution is complete or if it can be retried. An execution is considered complete only when
   * all configured policies consider the execution complete.
   */
  boolean isComplete();

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or already complete, the resulting {@link
   * CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void record(R result, Throwable failure);

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void recordResult(R result);

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void recordFailure(Throwable failure);
}