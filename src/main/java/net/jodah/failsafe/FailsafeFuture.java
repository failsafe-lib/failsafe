package net.jodah.failsafe;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.failsafe.function.CheckedBiFunction;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.ReentrantCircuit;

/**
 * The future result of an asynchronous Failsafe execution.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class FailsafeFuture<T> implements Future<T> {
  private final ReentrantCircuit circuit = new ReentrantCircuit();
  private final FailsafeConfig<T, ?> config;
  private ExecutionContext execution;
  private java.util.concurrent.CompletableFuture<T> completableFuture;

  // Mutable state
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile boolean cancelled;
  private volatile T result;
  private volatile Throwable failure;

  FailsafeFuture(FailsafeConfig<T, ?> config) {
    this.config = config;
    circuit.open();
  }

  /**
   * Attempts to cancel this execution. This attempt will fail if the execution has already completed, has already been
   * cancelled, or could not be cancelled for some other reason. If successful, and this execution has not started when
   * {@code cancel} is called, this execution should never run. If the execution has already started, then the
   * {@code mayInterruptIfRunning} parameter determines whether the thread executing this task should be interrupted in
   * an attempt to stop the execution.
   *
   * <p>
   * After this method returns, subsequent calls to {@link #isDone} will always return {@code true}. Subsequent calls to
   * {@link #isCancelled} will always return {@code true} if this method returned {@code true}.
   *
   * @param mayInterruptIfRunning {@code true} if the thread executing this execution should be interrupted; otherwise,
   *          in-progress executions are allowed to complete
   * @return {@code false} if the execution could not be cancelled, typically because it has already completed normally;
   *         {@code true} otherwise
   */
  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (done)
      return false;

    boolean cancelResult = delegate.cancel(mayInterruptIfRunning);
    failure = new CancellationException();
    cancelled = true;
    config.handleComplete(null, failure, execution, false);
    complete(null, failure, config.fallback, false);
    return cancelResult;
  }

  /**
   * Waits if necessary for the execution to complete, and then returns its result.
   *
   * @return the execution result
   * @throws CancellationException if the execution was cancelled
   * @throws ExecutionException if the execution threw an exception
   * @throws InterruptedException if the current thread was interrupted while waiting
   */
  @Override
  public T get() throws InterruptedException, ExecutionException {
    circuit.await();
    if (failure != null) {
      if (failure instanceof CancellationException)
        throw (CancellationException) failure;
      throw new ExecutionException(failure);
    }
    return result;
  }

  /**
   * Waits if necessary for at most the given time for the execution to complete, and then returns its result, if
   * available.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return the execution result
   * @throws CancellationException if the execution was cancelled
   * @throws ExecutionException if the execution threw an exception
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws TimeoutException if the wait timed out
   * @throws NullPointerException if {@code unit} is null
   * @throws IllegalArgumentException if {@code timeout} is < 0
   */
  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    Assert.isTrue(timeout >= 0, "timeout cannot be negative");
    if (!circuit.await(timeout, Assert.notNull(unit, "unit")))
      throw new TimeoutException();
    if (failure != null)
      throw new ExecutionException(failure);
    return result;
  }

  /**
   * Returns {@code true} if this execution was cancelled before it completed normally.
   *
   * @return {@code true} if this execution was cancelled before it completed
   */
  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * Returns {@code true} if this execution completed.
   *
   * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method
   * will return {@code true}.
   *
   * @return {@code true} if this execution completed
   */
  @Override
  public boolean isDone() {
    return done;
  }

  synchronized void complete(T result, Throwable failure, CheckedBiFunction<T, Throwable, T> fallback,
      boolean success) {
    if (done)
      return;

    if (fallback == null) {
      this.result = result;
      this.failure = failure;
    } else if (!success) {
      try {
        this.result = fallback.apply(result, failure);
      } catch (Throwable fallbackFailure) {
        this.failure = fallbackFailure;
      }
    }

    done = true;
    if (completableFuture != null)
      completeFuture();
    circuit.close();
  }

  void inject(java.util.concurrent.CompletableFuture<T> completableFuture) {
    this.completableFuture = completableFuture;
  }

  void inject(Future<T> delegate) {
    this.delegate = delegate;
  }

  void inject(ExecutionContext execution) {
    this.execution = execution;
  }

  private void completeFuture() {
    if (failure == null)
      completableFuture.complete(result);
    else
      completableFuture.completeExceptionally(failure);
  }
}
