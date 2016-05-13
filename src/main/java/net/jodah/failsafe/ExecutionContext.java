package net.jodah.failsafe;

import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.util.Duration;

/**
 * Contextual execution information.
 * 
 * @author Jonathan Halterman
 */
public class ExecutionContext {
  final Duration startTime;
  /** Number of execution attempts */
  volatile int executions;

  ExecutionContext(Duration startTime) {
    this.startTime = startTime;
  }

  ExecutionContext(ExecutionContext context) {
    this.startTime = context.startTime;
    this.executions = context.executions;
  }

  /**
   * Returns the elapsed time since initial execution began.
   */
  public Duration getElapsedTime() {
    return new Duration(System.nanoTime() - startTime.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Gets the number of executions so far.
   */
  public int getExecutions() {
    return executions;
  }

  /**
   * Returns the time that the initial execution started.
   */
  public Duration getStartTime() {
    return startTime;
  }

  ExecutionContext copy() {
    return new ExecutionContext(this);
  }
}
