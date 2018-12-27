/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.internal.util;

import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.*;

/**
 * A ScheduledExecutorService implementation that executes tasks on the ForkJoinPool's common thread pool and schedules
 * delayed executions on an internal, common ScheduledExecutorService.
 *
 * @author Jonathan Halterman
 * @author Ben Manes
 */
public final class CommonPoolScheduler implements Scheduler {
  private static final ExecutorService commonPool = ForkJoinPool.commonPool();
  private static volatile ScheduledExecutorService delayer;

  private static ScheduledExecutorService delayer() {
    if (delayer == null) {
      synchronized (CommonPoolScheduler.class) {
        if (delayer == null)
          delayer = new ScheduledThreadPoolExecutor(1, new DelayerThreadFactory());
      }
    }
    return delayer;
  }

  static final class DelayerThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("FailsafeDelayScheduler");
      return t;
    }
  }

  static final class DelayedExecutor implements Executor {
    final long delay;
    final TimeUnit unit;
    final Executor executor;

    DelayedExecutor(long delay, TimeUnit unit, Executor executor) {
      this.delay = delay;
      this.unit = unit;
      this.executor = executor;
    }

    public void execute(Runnable runnable) {
      delayer().schedule(() -> commonPool.execute(runnable), delay, unit);
    }
  }

  static final class ScheduledCompletableFuture<V> implements ScheduledFuture<V> {
    private final CompletableFuture<V> delegate;
    private final long time;

    ScheduledCompletableFuture(CompletableFuture<V> delegate, long delay, TimeUnit unit) {
      this.time = System.nanoTime() + unit.toNanos(delay);
      this.delegate = Assert.notNull(delegate, "delegate");
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(time - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
      if (other == this) {
        return 0;
      } else if (other instanceof ScheduledCompletableFuture) {
        return Long.compare(time, ((ScheduledCompletableFuture<?>) other).time);
      }
      return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      return delegate.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return delegate.get(timeout, unit);
    }
  }

  @Override
  public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
    Executor executor = delay == 0 ? commonPool : new DelayedExecutor(delay, unit, commonPool);
    CompletableFuture<?> future = CompletableFuture.supplyAsync(Unchecked.supplier(callable), executor);
    return new ScheduledCompletableFuture<>(future, delay, unit);
  }
}