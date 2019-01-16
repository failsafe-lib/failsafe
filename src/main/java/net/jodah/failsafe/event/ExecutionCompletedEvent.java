package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

/**
 * Indicates an execution was completed.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionCompletedEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable failure;

  public ExecutionCompletedEvent(R result, Throwable failure, ExecutionContext context) {
    super(context);
    this.result = result;
    this.failure = failure;
  }

  /**
   * Returns the failure that preceeded the event, else {@code null} if there was none.
   */
  public Throwable getFailure() {
    return failure;
  }

  /**
   * Returns the result that preceeded the event, else {@code null} if there was none.
   */
  public R getResult() {
    return result;
  }

  @Override
  public String toString() {
    return "ExecutionCompletedEvent[" + "result=" + result + ", failure=" + failure + ']';
  }
}
