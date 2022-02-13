/*
 * Copyright 2022 the original author or authors.
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
package dev.failsafe.retrofit;

import dev.failsafe.*;
import dev.failsafe.internal.util.Assert;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Failsafe wrapped Retrofit {@link Call}. Supports synchronous and asynchronous executions, and cancellation.
 *
 * @param <T> response type
 * @author Jonathan Halterman
 */
public final class FailsafeCall<T> {
  private final FailsafeExecutor<Response<T>> failsafe;
  private final retrofit2.Call<T> initialCall;

  private volatile Call<Response<T>> failsafeCall;
  private volatile CompletableFuture<Response<T>> failsafeFuture;
  private AtomicBoolean cancelled = new AtomicBoolean();
  private AtomicBoolean executed = new AtomicBoolean();

  private FailsafeCall(retrofit2.Call<T> call, FailsafeExecutor<Response<T>> failsafe) {
    this.initialCall = call;
    this.failsafe = failsafe;
  }

  /**
   * Returns a FailsafeCall for the {@code call}, {@code outerPolicy} and {@code policies}. See {@link
   * Failsafe#with(Policy, Policy[])} for docs on how policy composition works.
   *
   * @param <T> response type
   * @param <P> policy type
   * @throws NullPointerException if {@code call} or {@code outerPolicy} are null
   */
  @SafeVarargs
  public static <T, P extends Policy<Response<T>>> FailsafeCall<T> of(retrofit2.Call<T> call, P outerPolicy,
    P... policies) {
    return of(call, Failsafe.with(outerPolicy, policies));
  }

  /**
   * Returns a FailsafeCall for the {@code call} and {@code failsafeExecutor}.
   *
   * @param <T> response type
   * @throws NullPointerException if {@code call} or {@code failsafeExecutor} are null
   */
  public static <T> FailsafeCall<T> of(retrofit2.Call<T> call, FailsafeExecutor<Response<T>> failsafeExecutor) {
    return new FailsafeCall<>(Assert.notNull(call, "call"), Assert.notNull(failsafeExecutor, "failsafeExecutor"));
  }

  /**
   * Cancels the call.
   */
  public void cancel() {
    if (!cancelled.compareAndSet(false, true))
      return;
    if (failsafeCall != null)
      failsafeCall.cancel();
    if (failsafeFuture != null)
      failsafeFuture.cancel(false);
  }

  /**
   * Returns a clone of the FailsafeCall.
   */
  public FailsafeCall<T> clone() {
    return FailsafeCall.of(initialCall.clone(), failsafe);
  }

  /**
   * Executes the call until a successful response is returned or the configured policies are exceeded.
   *
   * @throws IllegalStateException if the call has already been executed
   * @throws IOException if the request could not be executed due to cancellation, a connectivity problem, or timeout
   * @throws FailsafeException if the execution fails with a checked Exception. {@link FailsafeException#getCause()} can
   * be used to learn the underlying checked exception.
   */
  public Response<T> execute() throws IOException {
    Assert.isTrue(executed.compareAndSet(false, true), "already executed");

    failsafeCall = failsafe.getCall(ctx -> {
      return prepareCall(ctx).execute();
    });

    try {
      return failsafeCall.execute();
    } catch (FailsafeException e) {
      if (e.getCause() instanceof IOException)
        throw (IOException) e.getCause();
      throw e;
    }
  }

  /**
   * Executes the call asynchronously until a successful result is returned or the configured policies are exceeded.
   */
  public CompletableFuture<Response<T>> executeAsync() {
    if (!executed.compareAndSet(false, true)) {
      CompletableFuture<Response<T>> result = new CompletableFuture<>();
      result.completeExceptionally(new IllegalStateException("already executed"));
      return result;
    }

    failsafeFuture = failsafe.getAsyncExecution(exec -> {
      prepareCall(exec).enqueue(new Callback<T>() {
        @Override
        public void onResponse(retrofit2.Call<T> call, Response<T> response) {
          exec.recordResult(response);
        }

        @Override
        public void onFailure(retrofit2.Call<T> call, Throwable throwable) {
          exec.recordException(throwable);
        }
      });
    });

    return failsafeFuture;
  }

  /**
   * Returns whether the call has been cancelled.
   */
  public boolean isCancelled() {
    return cancelled.get();
  }

  /**
   * Returns whether the call has been executed.
   */
  public boolean isExecuted() {
    return executed.get();
  }

  private retrofit2.Call<T> prepareCall(ExecutionContext<Response<T>> ctx) {
    retrofit2.Call<T> call = ctx.isFirstAttempt() ? initialCall : initialCall.clone();

    // Propagate cancellation to the call
    ctx.onCancel(() -> {
      cancelled.set(true);
      call.cancel();
    });
    return call;
  }
}
