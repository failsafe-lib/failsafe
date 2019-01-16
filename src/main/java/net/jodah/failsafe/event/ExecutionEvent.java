package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

import java.time.Duration;

/**
 * Encapsulates information about a Failsafe execution.
 *
 * @author Jonathan Halterman
 */
public class ExecutionEvent {
  private final ExecutionContext context;

  public ExecutionEvent(ExecutionContext context) {
    this.context = context;
  }

  /**
   * Returns the elapsed time since initial execution began.
   */
  public Duration getElapsedTime() {
    return context.getElapsedTime();
  }

  /**
   * Gets the number of executions so far.
   */
  public int getExecutions() {
    return context.getExecutions();
  }

  /**
   * Returns the time that the initial execution started.
   */
  public Duration getStartTime() {
    return context.getStartTime();
  }
}
