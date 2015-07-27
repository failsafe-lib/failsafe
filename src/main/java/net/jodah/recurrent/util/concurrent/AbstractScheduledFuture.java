package net.jodah.recurrent.util.concurrent;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AbstractScheduledFuture<T> implements ScheduledFuture<T> {
  @Override
  public long getDelay(TimeUnit unit) {
    return 0;
  }

  @Override
  public int compareTo(Delayed o) {
    return 0;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return false;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return null;
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return null;
  }
}
