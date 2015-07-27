package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@link Scheduler} utilities.
 * 
 * @author Jonathan Halterman
 */
public final class Schedulers {
  private Schedulers() {
  }

  public static Scheduler of(final ScheduledExecutorService executor) {
    return new Scheduler() {
      @Override
      public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
      }
    };
  }
}
