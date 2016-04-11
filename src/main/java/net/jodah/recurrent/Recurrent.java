package net.jodah.recurrent;

import java.util.concurrent.ScheduledExecutorService;

import net.jodah.recurrent.util.concurrent.Scheduler;
import net.jodah.recurrent.util.concurrent.Schedulers;

/**
 * Performs invocations with synchronous or asynchronous retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Recurrent<T> {
  /**
   * Creates and returns a new SyncRecurrent instance that will perform invocations and retries synchronously according
   * to the {@code retryPolicy}.
   */
  public static SyncRecurrent with(RetryPolicy retryPolicy) {
    return new SyncRecurrent(retryPolicy);
  }

  /**
   * Creates and returns a new AsyncRecurrent instance that will perform invocations and retries asynchronously via the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, ScheduledExecutorService executor) {
    return new AsyncRecurrent(retryPolicy, Schedulers.of(executor));
  }

  /**
   * Creates and returns a new AsyncRecurrent instance that will perform invocations and retries asynchronously via the
   * {@code scheduler} according to the {@code retryPolicy}.
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, Scheduler scheduler) {
    return new AsyncRecurrent(retryPolicy, scheduler);
  }
}
