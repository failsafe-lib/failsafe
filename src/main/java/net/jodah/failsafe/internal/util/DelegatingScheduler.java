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
 * A {@link Scheduler} implementation that schedules delays on an internal, common ScheduledExecutorService and executes
 * tasks on either a provided ExecutorService or {@link ForkJoinPool#commonPool()}. Supports cancellation of {@link
 * ForkJoinPool} tasks.
 *
 * @author Jonathan Halterman
 * @author Ben Manes
 */
public final class DelegatingScheduler implements Scheduler {
  public static final DelegatingScheduler INSTANCE = new DelegatingScheduler();
  private static final ExecutorService COMMON_POOL = ForkJoinPool.commonPool();
  private static volatile ScheduledExecutorService delayer;

  private final ExecutorService executorService;

  private DelegatingScheduler() {
    this.executorService = null;
  }

  public DelegatingScheduler(ExecutorService executor) {
    this.executorService = executor;
  }

  private static ScheduledExecutorService delayer() {
    if (delayer == null) {
      synchronized (DelegatingScheduler.class) {
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

  static final class ScheduledCompletableFuture<V> extends CompletableFuture<V> implements ScheduledFuture<V> {
    // Guarded by this
    volatile Future<V> delegate;
    // Guarded by this
    Thread forkJoinPoolThread;
    private final long time;

    ScheduledCompletableFuture(long delay, TimeUnit unit) {
      this.time = System.nanoTime() + unit.toNanos(delay);
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
      boolean result = super.cancel(mayInterruptIfRunning);
      synchronized(this) {
        if (delegate != null)
          result = delegate.cancel(mayInterruptIfRunning);
        if (forkJoinPoolThread != null && mayInterruptIfRunning)
          forkJoinPoolThread.interrupt();
      }
      return result;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
    ScheduledCompletableFuture promise = new ScheduledCompletableFuture<>(delay, unit);
    ExecutorService es = executorService != null ? executorService : COMMON_POOL;
    boolean isForkJoinPool = es instanceof ForkJoinPool;
    Callable<?> completingCallable = () -> {
      try {
        if (isForkJoinPool) {
          // Guard against race with promise.cancel 
          synchronized (promise) {
            promise.forkJoinPoolThread = Thread.currentThread();
          }
        }
        promise.complete(callable.call());
        synchronized (promise) {
          promise.forkJoinPoolThread = null;
        }
      } catch (Throwable t) {
        promise.completeExceptionally(t);
      }
      return null;
    };

    if (delay == 0)
      promise.delegate = es.submit(completingCallable);
    else
      promise.delegate = delayer().schedule(() -> {
        // Guard against race with promise.cancel
        synchronized (promise) {
          if (!promise.isCancelled())
            promise.delegate = es.submit(completingCallable);
        }
      }, delay, unit);

    return promise;
  }
}