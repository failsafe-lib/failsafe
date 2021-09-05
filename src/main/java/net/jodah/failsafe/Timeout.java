package net.jodah.failsafe;

import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A policy that cancels and fails an excecution with a {@link net.jodah.failsafe.TimeoutExceededException
 * TimeoutExceededException} if a timeout is exceeded. Execution {@link #withInterrupt(boolean) interruption} is
 * optionally supported. Asynchronous executions are cancelled by calling {@link java.util.concurrent.Future#cancel(boolean)
 * cancel} on their underlying future. Executions can internally cooperate with cancellation by checking {@link
 * ExecutionContext#isCancelled()}.
 * <p>
 * This policy uses a separate thread on the configured scheduler or the common pool to perform timeouts checks.
 * <p>
 * The {@link Timeout#onFailure(CheckedConsumer)} and {@link Timeout#onSuccess(CheckedConsumer)} event handlers can be
 * used to handle a timeout being exceeded or not.
 * </p>
 * <p>Note: {@link #withInterrupt(boolean) interruption} will have no effect when performing an {@link
 * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unkown to Failsafe.</p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutExceededException
 */
public class Timeout<R> extends PolicyListeners<Timeout<R>, R> implements Policy<R> {
  private final Duration timeout;
  private volatile boolean interruptable;

  private Timeout(Duration timeout) {
    this.timeout = timeout;
  }

  /**
   * Returns the timeout duration.
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * This method is deprecated and will be removed in a future minor release.
   *
   * @see #withCancel(boolean)
   * @deprecated Always returns {@code true}
   */
  public boolean canCancel() {
    return true;
  }

  /**
   * Returns whether the policy can interrupt an execution if the timeout is exceeded.
   *
   * @see #withInterrupt(boolean)
   */
  public boolean canInterrupt() {
    return interruptable;
  }

  /**
   * This method is deprecated and will be removed in a future minor release.
   *
   * @see #withInterrupt(boolean)
   * @deprecated Tasks are cancelled by default when a Timeout is exceeded. Use {@link #withInterrupt(boolean)}
   * interrupt cancelled tasks when a Timeout is exceeded.
   */
  public Timeout<R> withCancel(boolean mayInterruptIfRunning) {
    interruptable = mayInterruptIfRunning;
    return this;
  }

  /**
   * When {@code mayInterruptIfRunning} is {@code true}, configures the policy to interrupt an execution in addition to
   * cancelling it when the timeout is exceeded. For synchronous executions this is done by calling {@link
   * Thread#interrupt()} on the execution's thread. For asynchronous executions this is done by calling {@link
   * java.util.concurrent.Future#cancel(boolean) Future.cancel(true)}. Executions can internally cooperate with
   * interruption by checking {@link Thread#isInterrupted()} or by handling {@link InterruptedException} where
   * available.
   * <p>
   * Note: Only configure interrupts if the code being executed is designed to be interrupted.
   * <p>
   * <p>Note: {@link #withInterrupt(boolean) interruption} will have no effect when performing an {@link
   * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unkown to
   * Failsafe.</p>
   *
   * @param mayInterruptIfRunning whether to enable interruption or not
   */
  public Timeout<R> withInterrupt(boolean mayInterruptIfRunning) {
    interruptable = mayInterruptIfRunning;
    return this;
  }

  /**
   * Returns a {@link Timeout} that fails an execution with {@link net.jodah.failsafe.TimeoutExceededException
   * TimeoutExceededException} if it exceeds the {@code timeout}.
   *
   * @param timeout the duration after which an execution is failed with {@link net.jodah.failsafe.TimeoutExceededException
   * TimeoutExceededException}.
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   */
  public static <R> Timeout<R> of(Duration timeout) {
    Assert.notNull(timeout, "timeout");
    Assert.isTrue(timeout.toNanos() > 0, "timeout must be > 0");
    return new Timeout<>(timeout);
  }

  @Override
  public PolicyExecutor toExecutor(AbstractExecution<R> execution) {
    return new TimeoutExecutor<>(this, execution);
  }
}
