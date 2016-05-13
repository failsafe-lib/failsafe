package net.jodah.failsafe.util.concurrent;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A default ScheduledFuture implementation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class DefaultScheduledFuture<T> implements ScheduledFuture<T> {
  /**
   * @return {@code 0}
   */
  @Override
  public long getDelay(TimeUnit unit) {
    return 0;
  }

  /**
   * @return {@code 0}
   */
  @Override
  public int compareTo(Delayed o) {
    return 0;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean isCancelled() {
    return false;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean isDone() {
    return false;
  }

  /**
   * @return {@code null}
   */
  @Override
  public T get() throws InterruptedException, ExecutionException {
    return null;
  }

  /**
   * @return {@code null}
   */
  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return null;
  }
}