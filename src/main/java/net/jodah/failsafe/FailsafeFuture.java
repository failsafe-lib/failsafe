/*
 * Copyright 2016 the original author or authors.
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
package net.jodah.failsafe;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * A CompletableFuture implementation that propogates cancellations.
 *
 * @param <T> result type
 * @author Jonathan Halterman
 */
public class FailsafeFuture<T> extends CompletableFuture<T> {
  private final FailsafeExecutor<T> executor;
  private ExecutionContext execution;

  // Mutable state
  private Future<T> delegate;

  FailsafeFuture(FailsafeExecutor<T> executor) {
    this.executor = executor;
  }

  /**
   * Cancels this and the internal delegate.
   */
  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone())
      return false;

    boolean cancelResult = super.cancel(mayInterruptIfRunning);
    if (delegate != null)
      cancelResult = delegate.cancel(mayInterruptIfRunning);
    Throwable failure = new CancellationException();
    complete(null, failure);
    executor.handleComplete(ExecutionResult.failure(failure), execution);
    return cancelResult;
  }

  synchronized void complete(T result, Throwable failure) {
    if (isDone())
      return;

    if (failure != null)
      super.completeExceptionally(failure);
    else
      super.complete(result);
  }

  public synchronized void inject(Future<T> delegate) {
    this.delegate = delegate;
  }

  void inject(ExecutionContext execution) {
    this.execution = execution;
  }
}
