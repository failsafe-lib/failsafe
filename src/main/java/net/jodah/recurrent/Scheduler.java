package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Schedules work for execution.
 * 
 * @author Jonathan Halterman
 */
public interface Scheduler {
  /**
   * Schedules the {@code callable} to execute after the {@code delay}.
   */
  <T> Future<T> schedule(Callable<T> callable, long delay, TimeUnit unit);
}
