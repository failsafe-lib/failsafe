package net.jodah.recurrent;

import java.util.concurrent.ScheduledExecutorService;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;
import net.jodah.recurrent.util.concurrent.Schedulers;

/**
 * Performs executions with synchronous or asynchronous retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Recurrent<T> {
  /**
   * Creates and returns a new SyncRecurrent instance that will perform executions and retries synchronously according
   * to the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public static SyncRecurrent with(RetryPolicy retryPolicy) {
    return new SyncRecurrent(Assert.notNull(retryPolicy, "retryPolicy"));
  }

  /**
   * Creates and returns a new AsyncRecurrent instance that will perform executions and retries asynchronously via the
   * {@code executor} according to the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} or {@code executor} are null
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, ScheduledExecutorService executor) {
    return new AsyncRecurrent(Assert.notNull(retryPolicy, "retryPolicy"), Schedulers.of(executor));
  }

  /**
   * Creates and returns a new AsyncRecurrent instance that will perform executions and retries asynchronously via the
   * {@code scheduler} according to the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} or {@code scheduler} are null
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, Scheduler scheduler) {
    return new AsyncRecurrent(Assert.notNull(retryPolicy, "retryPolicy"), Assert.notNull(scheduler, "scheduler"));
  }
}
