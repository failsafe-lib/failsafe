package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.Lists;
import net.jodah.failsafe.spi.Policy;

import java.time.Duration;

/**
 * Tracks synchronous executions and handles failures according to one or more {@link Policy policies}. Execution
 * results must be explicitly recorded via one of the {@code record} methods.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface Execution<R> extends ExecutionContext<R> {
  /**
   * Creates a new {@link SyncExecutionImpl} that will use the {@code outerPolicy} and {@code innerPolicies} to
   * handle failures. Policies are applied in reverse order, with the last policy being applied first.
   *
   * @throws NullPointerException if {@code outerPolicy} is null
   */
  @SafeVarargs
  static <R> Execution<R> of(Policy<R> outerPolicy, Policy<R>... policies) {
    return new SyncExecutionImpl<>(Lists.of(Assert.notNull(outerPolicy, "outerPolicy"), policies));
  }

  /**
   * Records and completes the execution successfully.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void complete();

  /**
   * Returns whether the execution is complete or if it can be retried. An execution is considered complete only when
   * all configured policies consider the execution complete.
   */
  boolean isComplete();

  /**
   * Returns the time to delay before the next execution attempt. Returns {@code 0} if an execution has not yet
   * occurred.
   */
  Duration getDelay();

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(R result, Throwable failure);

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void recordResult(R result);

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void recordFailure(Throwable failure);
}
