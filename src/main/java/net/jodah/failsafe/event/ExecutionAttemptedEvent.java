package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

/**
 * Indicates an execution was attempted.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionAttemptedEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable failure;

  public ExecutionAttemptedEvent(R result, Throwable failure, ExecutionContext context) {
    super(context);
    this.result = result;
    this.failure = failure;
  }

  /**
   * Returns the failure that preceeded the event, else {@code null} if there was none.
   */
  public Throwable getLastFailure() {
    return failure;
  }

  /**
   * Returns the result that preceeded the event, else {@code null} if there was none.
   */
  public R getLastResult() {
    return result;
  }

  @Override
  public String toString() {
    return "ExecutionAttemptedEvent[" + "result=" + result + ", failure=" + failure + ']';
  }
}
