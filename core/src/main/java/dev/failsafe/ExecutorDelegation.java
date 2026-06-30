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
package dev.failsafe;

import dev.failsafe.function.AsyncRunnable;
import dev.failsafe.function.ContextualSupplier;

import java.util.concurrent.Executor;

/**
 * Wraps a {@link ContextualSupplier} or {@link AsyncRunnable} so that it runs on a user-supplied {@link Executor}
 * rather than the calling thread, and propagates any resulting throwable back out of the executor thread.
 * <p>
 * Extracted from {@link Functions}, which previously combined this executor-delegation concern with
 * execution-pipeline wiring and generic function adaptation in a single class with no shared state across the
 * three concerns.
 *
 * @author Jonathan Halterman
 */
final class ExecutorDelegation {
  static <R, T> ContextualSupplier<R, T> withExecutor(ContextualSupplier<R, T> supplier, Executor executor) {
    return executor == null ? supplier : ctx -> {
      executor.execute(() -> {
        try {
          supplier.get(ctx);
        } catch (Throwable e) {
          handleExecutorThrowable(e);
        }
      });
      return null;
    };
  }

  static <R> AsyncRunnable<R> withExecutor(AsyncRunnable<R> runnable, Executor executor) {
    return executor == null ? runnable : exec -> {
      executor.execute(() -> {
        try {
          runnable.run(exec);
        } catch (Throwable e) {
          handleExecutorThrowable(e);
        }
      });
    };
  }

  private static void handleExecutorThrowable(Throwable e) {
    if (e instanceof RuntimeException)
      throw (RuntimeException) e;
    if (e instanceof Error)
      throw (Error) e;
    throw new FailsafeException(e);
  }
}