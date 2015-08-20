package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;

/**
 * {@link Scheduler} utilities.
 * 
 * @author Jonathan Halterman
 * @see net.jodah.recurrent.util.concurrent.DefaultScheduledFuture
 */
public final class Schedulers {
  private Schedulers() {
  }

  /**
   * Returns a Scheduler adapted from the {@code executor}.
   * 
   * @throws NullPointerException if {@code executor} is null
   */
  public static Scheduler of(final ScheduledExecutorService executor) {
    Assert.notNull(executor, "executor");
    return new Scheduler() {
      @Override
      public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
      }
    };
  }
}
