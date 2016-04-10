package net.jodah.recurrent;

import java.util.concurrent.Callable;

public interface SyncRecurrent {
  /**
   * Invokes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  <T> T get(Callable<T> callable);

  /**
   * Invokes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  <T> T get(ContextualCallable<T> callable);

  /**
   * Invokes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  void run(CheckedRunnable runnable);

  /**
   * Invokes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  void run(ContextualRunnable runnable);

  /**
   * Configures the {@code listeners} to be called as invocation events occur.
   */
  SyncRecurrent with(Listeners<?> listeners);
}