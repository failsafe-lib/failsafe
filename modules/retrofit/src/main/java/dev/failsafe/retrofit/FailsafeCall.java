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
 * @param <R> response type
 * @author Jonathan Halterman
 */
public final class FailsafeCall<R> {
  private final FailsafeExecutor<Response<R>> failsafe;
  private final retrofit2.Call<R> initialCall;

  private volatile Call<Response<R>> failsafeCall;
  private volatile CompletableFuture<Response<R>> failsafeFuture;
  private AtomicBoolean cancelled = new AtomicBoolean();
  private AtomicBoolean executed = new AtomicBoolean();

  private FailsafeCall(FailsafeExecutor<Response<R>> failsafe, retrofit2.Call<R> call) {
    this.failsafe = failsafe;
    this.initialCall = call;
  }

  public static final class FailsafeCallBuilder<R> {
    private FailsafeExecutor<Response<R>> failsafe;

    private FailsafeCallBuilder(FailsafeExecutor<Response<R>> failsafe) {
      this.failsafe = failsafe;
    }

    public <P extends Policy<Response<R>>> FailsafeCallBuilder<R> compose(P innerPolicy) {
      failsafe = failsafe.compose(innerPolicy);
      return this;
    }

    public FailsafeCall<R> compose(retrofit2.Call<R> call) {
      return new FailsafeCall<>(failsafe, call);
    }
  }

  /**
   * Returns a FailsafeCallBuilder for the {@code outerPolicy} and {@code policies}. See {@link Failsafe#with(Policy,
   * Policy[])} for docs on how policy composition works.
   *
   * @param <R> result type
   * @param <P> policy type
   * @throws NullPointerException if {@code call} or {@code outerPolicy} are null
   */
  @SafeVarargs
  public static <R, P extends Policy<Response<R>>> FailsafeCallBuilder<R> with(P outerPolicy, P... policies) {
    return new FailsafeCallBuilder<>(Failsafe.with(outerPolicy, policies));
  }

  /**
   * Returns a FailsafeCallBuilder for the {@code failsafeExecutor}.
   *
   * @param <R> result type
   * @throws NullPointerException if {@code failsafeExecutor} is null
   */
  public static <R> FailsafeCallBuilder<R> with(FailsafeExecutor<Response<R>> failsafeExecutor) {
    return new FailsafeCallBuilder<>(Assert.notNull(failsafeExecutor, "failsafeExecutor"));
  }

  /**
   * Cancels the call.
   */
  public void cancel() {
    if (!cancelled.compareAndSet(false, true))
      return;
    if (failsafeCall != null)
      failsafeCall.cancel(false);
    if (failsafeFuture != null)
      failsafeFuture.cancel(false);
  }

  /**
   * Returns a clone of the FailsafeCall.
   */
  public FailsafeCall<R> clone() {
    return new FailsafeCall<>(failsafe, initialCall.clone());
  }

  /**
   * Executes the call until a successful response is returned or the configured policies are exceeded.
   *
   * @throws IllegalStateException if the call has already been executed
   * @throws IOException if the request could not be executed due to cancellation, a connectivity problem, or timeout
   * @throws FailsafeException if the execution fails with a checked Exception. {@link FailsafeException#getCause()} can
   * be used to learn the underlying checked exception.
   */
  public Response<R> execute() throws IOException {
    Assert.isTrue(executed.compareAndSet(false, true), "already executed");

    failsafeCall = failsafe.newCall(ctx -> {
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
  public CompletableFuture<Response<R>> executeAsync() {
    if (!executed.compareAndSet(false, true)) {
      CompletableFuture<Response<R>> result = new CompletableFuture<>();
      result.completeExceptionally(new IllegalStateException("already executed"));
      return result;
    }

    failsafeFuture = failsafe.getAsyncExecution(exec -> {
      prepareCall(exec).enqueue(new Callback<R>() {
        @Override
        public void onResponse(retrofit2.Call<R> call, Response<R> response) {
          exec.recordResult(response);
        }

        @Override
        public void onFailure(retrofit2.Call<R> call, Throwable throwable) {
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

  private retrofit2.Call<R> prepareCall(ExecutionContext<Response<R>> ctx) {
    retrofit2.Call<R> call = ctx.isFirstAttempt() ? initialCall : initialCall.clone();

    // Propagate cancellation to the call
    ctx.onCancel(() -> {
      cancelled.set(true);
      call.cancel();
    });
    return call;
  }
}
