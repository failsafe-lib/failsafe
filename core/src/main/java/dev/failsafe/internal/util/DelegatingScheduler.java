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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.ForkJoinPool.commonPool;

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
  public static final DelegatingScheduler INSTANCE = new DelegatingScheduler(null,null);

  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduler;
  private final int executorType;

  private static final int EX_FORK_JOIN = 1;
  private static final int EX_COMMON    = 4;
  private static final int EX_INTERNAL  = 8;


  public DelegatingScheduler(ExecutorService executor) {
    this(executor, null);
  }

  public DelegatingScheduler(ExecutorService executor, ScheduledExecutorService scheduler) {
    if (executor == null || executor == commonPool()) {
      if (ForkJoinPool.getCommonPoolParallelism() > 1) {// @see CompletableFuture#useCommonPool
        executorService = commonPool();
        executorType = EX_COMMON   | EX_FORK_JOIN;

      } else {// don't use commonPool(): cannot support parallelism
        executorService = null;
        executorType = EX_INTERNAL | EX_FORK_JOIN;
      }
    } else {
      executorService = executor;
      executorType = executor instanceof ForkJoinPool ? EX_FORK_JOIN
          : 0;
    }
    this.scheduler = scheduler;
  }

  DelegatingScheduler (byte flags) {
    executorService = null;  executorType = flags;  scheduler = null;
  }//new for tests

  private static final class LazyDelayerHolder extends ScheduledThreadPoolExecutor implements ThreadFactory {
    private static final ScheduledThreadPoolExecutor DELAYER = new LazyDelayerHolder();

    public LazyDelayerHolder(){
      super(1);
      setThreadFactory(this);
      setRemoveOnCancelPolicy(true);
    }

    @Override public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "FailsafeDelayScheduler");
      t.setDaemon(true);
      return t;
    }
  }

  private static final class LazyForkJoinPoolHolder {
    private static final ForkJoinPool FORK_JOIN_POOL = new ForkJoinPool(
        Math.max(Runtime.getRuntime().availableProcessors(), 2),
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null, true/*asyncMode*/);
  }

  static final class ScheduledCompletableFuture<V> extends CompletableFuture<V> implements ScheduledFuture<V> {
    // Guarded by this
    volatile Future<V> delegate;
    // Guarded by this
    Thread forkJoinPoolThread;

    @Override
    public long getDelay(TimeUnit unit){
      Future<V> f = delegate;
      return f instanceof Delayed ? ((Delayed) f).getDelay(unit)
          : 0; // we are executing now
    }

    @Override
    public int compareTo(Delayed other) {
      if (other == this)// ScheduledFuture<?> gives no extra info
        return 0;
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
  }//ScheduledCompletableFuture

  private ScheduledExecutorService delayer() {
    return scheduler != null ? scheduler
        : LazyDelayerHolder.DELAYER;
  }

  private ExecutorService executorService() {
    return executorService != null ? executorService
        : LazyForkJoinPoolHolder.FORK_JOIN_POOL;
  }

  @Override @SuppressWarnings({ "unchecked", "rawtypes" })
  public ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit) {
    ScheduledCompletableFuture promise = new ScheduledCompletableFuture<>();
    final Callable<?> completingCallable = (executorType & EX_FORK_JOIN) == EX_FORK_JOIN
      ? () -> {
        try {
          // Guard against race with promise.cancel
          synchronized (promise) {
            promise.forkJoinPoolThread = Thread.currentThread();
          }
          promise.complete(callable.call());
        } catch (Throwable t) {
          promise.completeExceptionally(t);
        } finally {
          synchronized (promise) {
            promise.forkJoinPoolThread = null;
          }
        }
        return null;
      }// else not ForkJoin  BTW: but why? Other ExecutorServices also support cancellation
      : () ->{
        try {
          promise.complete(callable.call());
        } catch (Throwable t) {
          promise.completeExceptionally(t);
        }
        return null;
      };

    if (delay <= 0) {
      promise.delegate = executorService().submit(completingCallable);
      return promise;
    }

    final Callable<Void> r;// use less memory: don't capture variable with commonPool

    if ((executorType & EX_COMMON) == EX_COMMON)
      r = ()->{ // Guard against race with promise.cancel
        synchronized(promise) {
          if (!promise.isCancelled())
            promise.delegate = commonPool().submit(completingCallable);
        }
        return null;
      };

    else if ((executorType & EX_INTERNAL) == EX_INTERNAL)
      r = ()->{// Guard against race with promise.cancel
        synchronized(promise) {
          if (!promise.isCancelled())
            promise.delegate = LazyForkJoinPoolHolder.FORK_JOIN_POOL.submit(completingCallable);
        }
        return null;
      };

    else {
      final ExecutorService es = executorService();
      r = ()->{// Guard against race with promise.cancel
        synchronized(promise){
          if (!promise.isCancelled())
            promise.delegate = es.submit(completingCallable);
        }
        return null;
      };
    }
    promise.delegate = delayer().schedule(r, delay, unit);
    return promise;
  }
}