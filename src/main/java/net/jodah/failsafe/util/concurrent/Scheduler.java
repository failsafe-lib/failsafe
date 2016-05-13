package net.jodah.failsafe.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules executions.
 * 
 * @author Jonathan Halterman
 * @see Schedulers
 * @see net.jodah.failsafe.util.concurrent.DefaultScheduledFuture
 */
public interface Scheduler {
  /**
   * Schedules the {@code callable} to be called after the {@code delay} for the {@code unit}.
   */
  ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit);
}
