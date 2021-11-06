package net.jodah.failsafe;

import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.internal.TimeoutImpl;

import java.time.Duration;

/**
 * Builds {@link Timeout} instances.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see Timeout
 * @see TimeoutExceededException
 */
public class TimeoutBuilder<R> {
  final Duration timeout;
  boolean canInterrupt;

  TimeoutBuilder(Duration timeout) {
    this.timeout = timeout;
  }

  /**
   * Builds a new {@link Timeout} using the builder's configuration.
   */
  public Timeout<R> build() {
    return new TimeoutImpl<>(new TimeoutConfig(timeout, canInterrupt));
  }

  /**
   * Configures the policy to interrupt an execution in addition to cancelling it when the timeout is exceeded. For
   * synchronous executions this is done by calling {@link Thread#interrupt()} on the execution's thread. For
   * asynchronous executions this is done by calling {@link java.util.concurrent.Future#cancel(boolean)
   * Future.cancel(true)}. Executions can internally cooperate with interruption by checking {@link
   * Thread#isInterrupted()} or by handling {@link InterruptedException} where available.
   * <p>
   * Note: Only configure interrupts if the code being executed is designed to be interrupted.
   * <p>
   * <p>Note: interruption will have no effect when performing an {@link
   * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unkown to
   * Failsafe.</p>
   */
  public TimeoutBuilder<R> withInterrupt() {
    canInterrupt = true;
    return this;
  }
}
