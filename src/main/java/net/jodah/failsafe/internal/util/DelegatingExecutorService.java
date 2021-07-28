package net.jodah.failsafe.internal.util;

import java.util.List;
import java.util.concurrent.*;

/**
 * Delegates executions to an underlying {@link Executor}.
 */
public class DelegatingExecutorService extends AbstractExecutorService {
  private final Executor executor;

  public DelegatingExecutorService(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void shutdown() {
  }

  @Override
  public List<Runnable> shutdownNow() {
    return null;
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return false;
  }

  @Override
  public void execute(Runnable command) {
    executor.execute(command);
  }
}
