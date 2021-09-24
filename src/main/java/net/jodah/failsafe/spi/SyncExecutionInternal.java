package net.jodah.failsafe.spi;

import net.jodah.failsafe.Execution;

/**
 * Internal execution APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface SyncExecutionInternal<R> extends ExecutionInternal<R>, Execution<R> {
  /**
   * Returns the initial execution for a series of execution attempts, prior to {@link #copy() copies} being made.
   * Useful for locking to perform atomic operations on the same execution reference.
   */
  SyncExecutionInternal<R> getInitial();

  /**
   * Returns whether the execution is currently interruptable.
   */
  boolean isInterruptable();

  /**
   * Returns whether the execution is currently interrupted.
   */
  boolean isInterrupted();

  /**
   * Sets whether the execution is currently {@code interruptable}.
   */
  void setInterruptable(boolean interruptable);

  /**
   * Sets whether the execution has been internally {@code interrupted}.
   */
  void setInterrupted(boolean interrupted);

  /**
   * Returns a new copy of the SyncExecutionInternal if it is not standalone, else returns {@code this} since standalone
   * executions are referenced externally and cannot be replaced.
   */
  SyncExecutionInternal<R> copy();
}
