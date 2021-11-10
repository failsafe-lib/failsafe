package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.internal.TimeoutImpl;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A policy that cancels and fails an excecution with a {@link net.jodah.failsafe.TimeoutExceededException
 * TimeoutExceededException} if a timeout is exceeded. Execution {@link TimeoutBuilder#withInterrupt() interruption} is
 * optionally supported. Asynchronous executions are cancelled by calling {@link java.util.concurrent.Future#cancel(boolean)
 * cancel} on their underlying future. Executions can internally cooperate with cancellation by checking {@link
 * ExecutionContext#isCancelled()}.
 * <p>
 * This policy uses a separate thread on the configured scheduler or the common pool to perform timeouts checks.
 * <p>
 * The {@link TimeoutBuilder#onFailure(EventListener)} and {@link TimeoutBuilder#onSuccess(EventListener)} event
 * handlers can be used to handle a timeout being exceeded or not.
 * </p>
 * <p>Note: {@link TimeoutBuilder#withInterrupt() interruption} will have no effect when performing an {@link
 * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unkown to Failsafe.</p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutBuilder
 * @see TimeoutExceededException
 */
public interface Timeout<R> extends Policy<R> {
  /**
   * Returns a {@link TimeoutBuilder} that builds {@link Timeout} instances with the given {@code timeout}.
   *
   * @param timeout the duration after which an execution is failed with {@link net.jodah.failsafe.TimeoutExceededException
   * TimeoutExceededException}.
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   */
  static <R> TimeoutBuilder<R> builder(Duration timeout) {
    Assert.notNull(timeout, "timeout");
    Assert.isTrue(timeout.toNanos() > 0, "timeout must be > 0");
    return new TimeoutBuilder<>(timeout);
  }

  /**
   * Alias for:
   * <pre>
   *   Timeout.builder(timeout).build();
   * </pre>
   * Returns a {@link Timeout} that fails an execution with {@link net.jodah.failsafe.TimeoutExceededException
   * TimeoutExceededException} if it exceeds the {@code timeout}. To configure other options on a Timeout, use {@link
   * #builder(Duration)} instead.
   *
   * @param timeout the duration after which an execution is failed with {@link net.jodah.failsafe.TimeoutExceededException
   * TimeoutExceededException}.
   * @param <R> result type
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   */
  static <R> Timeout<R> of(Duration timeout) {
    return new TimeoutImpl<>(new TimeoutConfig<>(timeout, false));
  }

  /**
   * Returns the {@link TimeoutConfig} that the Timeout was built with.
   */
  TimeoutConfig<R> getConfig();
}
