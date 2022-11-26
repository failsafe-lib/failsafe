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
package dev.failsafe.internal.util;

import dev.failsafe.spi.Scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Scheduler} implementation that schedules delays on an internal, common ScheduledExecutorService and executes
 * tasks on either a provided ExecutorService, {@link ForkJoinPool#commonPool()}, or an internal {@link ForkJoinPool}
 * instance. If no {@link ExecutorService} is supplied, the {@link ForkJoinPool#commonPool()} will be used, unless the
 * common pool's parallelism is 1, then an internal {@link ForkJoinPool} with parallelism of 2 will be created and
 * used.
 * <p>
 * Supports cancellation of {@link ForkJoinPool} tasks.
 * </p>
 *
 * @author Jonathan Halterman
 * @author Ben Manes
 */
public final class DelegatingScheduler implements Scheduler {
  public static final DelegatingScheduler INSTANCE = new DelegatingScheduler();

  private final ExecutorService executorService;

  private DelegatingScheduler() {
    this.executorService = null;
  }

  public DelegatingScheduler(ExecutorService executor){
    if (executor != ForkJoinPool.commonPool() || useCommonPool())
      this.executorService = executor;
    else // don't use commonPool(): cannot support parallelism @see CompletableFuture#useCommonPool
      this.executorService = null;
  }

  private static final class LazyDelayerHolder {
    private static final ScheduledThreadPoolExecutor DELAYER = create();

    private static ScheduledThreadPoolExecutor create() {
      ScheduledThreadPoolExecutor delayer = new ScheduledThreadPoolExecutor(1, LazyDelayerHolder::newThread);
      delayer.setRemoveOnCancelPolicy(true);
      return delayer;
    }

    public static Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("FailsafeDelayScheduler");
      return t;
    }
  }

  private static final class LazyForkJoinPoolHolder {
    private static final ForkJoinPool FORK_JOIN_POOL = create();

    private static ForkJoinPool create(){
      return useCommonPool() ? ForkJoinPool.commonPool()
          : new ForkJoinPool(Math.max(Runtime.getRuntime().availableProcessors(), 2),
              ForkJoinPool.defaultForkJoinWorkerThreadFactory,
              null, true);
    }
  }

  static boolean useCommonPool () {
    return ForkJoinPool.getCommonPoolParallelism() > 1;
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
      synchronized (this) {
        if (delegate != null)
          result = delegate.cancel(mayInterruptIfRunning);
        if (forkJoinPoolThread != null && mayInterruptIfRunning)
          forkJoinPoolThread.interrupt();
      }
      return result;
    }
  }

  private static ScheduledExecutorService delayer() {
    return LazyDelayerHolder.DELAYER;
  }

  private ExecutorService executorService() {
    return executorService != null
        ? executorService
        : LazyForkJoinPoolHolder.FORK_JOIN_POOL;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
    ScheduledCompletableFuture promise = new ScheduledCompletableFuture<>(delay, unit);
    ExecutorService es = executorService();
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
      } catch (Throwable t) {
        promise.completeExceptionally(t);
      } finally {
        if (isForkJoinPool) {
          synchronized (promise) {
            promise.forkJoinPoolThread = null;
          }
        }
      }
      return null;
    };

    if (delay <= 0)
      promise.delegate = es.submit(completingCallable);

    // use less memory: don't capture variable with commonPool
    else if (es == LazyForkJoinPoolHolder.FORK_JOIN_POOL)
      promise.delegate = delayer().schedule(()->{
        // Guard against race with promise.cancel
        synchronized(promise) {
          if (!promise.isCancelled())
            promise.delegate = LazyForkJoinPoolHolder.FORK_JOIN_POOL.submit(completingCallable);
        }
      }, delay, unit);
    else
      promise.delegate = delayer().schedule(()->{
        // Guard against race with promise.cancel
        synchronized(promise) {
          if (!promise.isCancelled())
            promise.delegate = es.submit(completingCallable);
        }
      }, delay, unit);

    return promise;
  }
}