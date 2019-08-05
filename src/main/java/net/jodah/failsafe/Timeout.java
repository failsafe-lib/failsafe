package net.jodah.failsafe;

import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.executor.TimeoutExecutor;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A policy that fails an excecution with a {@link java.util.concurrent.TimeoutException TimeoutException} if it exceeds
 * a timeout. Uses a separate thread on the configured scheduler or the common pool to perform timeouts checks.
 * <p>
 * The {@link Timeout#onFailure(CheckedConsumer)} and {@link Timeout#onSuccess(CheckedConsumer)} event handlers can be
 * used to handle a timeout being exceeded or not.
 * </p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class Timeout<R> extends PolicyListeners<Timeout<R>, R> implements Policy<R> {
  private final Duration timeout;
  private volatile boolean cancellable;
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
   * Returns whether the policy can cancel an execution if the timeout is exceeded.
   *
   * @see #withCancel(boolean)
   */
  public boolean canCancel() {
    return cancellable;
  }

  /**
   * Returns whether the policy can interrupt an execution if the timeout is exceeded.
   *
   * @see #withCancel(boolean)
   */
  public boolean canInterrupt() {
    return interruptable;
  }

  /**
   * Configures the policy to cancel an execution if it times out. Execution cancellation can be observed from within an
   * execution by checking {@link ExecutionContext#isCancelled()}, allowing execution to be gracefully stopped.
   * Asynchronous executions are cancelled by calling {@link java.util.concurrent.Future#cancel(boolean) cancel} on
   * their underlying future.
   * <p>
   * Note: Executions that are cancelled after they timeout are still completed with {@link
   * java.util.concurrent.TimeoutException TimeoutException}.
   * </p>
   *
   * @param mayInterruptIfRunning Whether the policy should interrupt an execution in addition to cancelling it if the
   * timeout is exceeded. When set to {@code true} the execution will be interrupted. For synchronous executions this is
   * done by calling {@link Thread#interrupt()} on the execution's thread. For asynchronous executions this is done by
   * calling {@link java.util.concurrent.Future#cancel(boolean) Future.cancel(true)}. Only set {@code
   * mayInterruptIfRunning} to {@code true} if the code being executed is designed to be interrupted.
   */
  public Timeout<R> withCancel(boolean mayInterruptIfRunning) {
    cancellable = true;
    interruptable = mayInterruptIfRunning;
    return this;
  }

  /**
   * Returns a {@link Timeout} that fails an execution with {@link java.util.concurrent.TimeoutException
   * TimeoutException} if it exceeds the {@code timeout}.
   *
   * @param timeout the duration after which an execution is failed with {@link java.util.concurrent.TimeoutException
   * TimeoutException}.
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   */
  public static <R> Timeout<R> of(Duration timeout) {
    Assert.notNull(timeout, "timeout");
    Assert.isTrue(timeout.toNanos() > 0, "timeout must be > 0");
    return new Timeout<>(timeout);
  }

  @Override
  public PolicyExecutor toExecutor(AbstractExecution execution) {
    return new TimeoutExecutor(this, execution);
  }
}
