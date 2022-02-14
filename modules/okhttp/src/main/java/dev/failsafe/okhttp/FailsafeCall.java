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
package dev.failsafe.okhttp;

import dev.failsafe.*;
import dev.failsafe.internal.util.Assert;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Failsafe wrapped OkHttp {@link Call}. Supports synchronous and asynchronous executions, and cancellation.
 *
 * @author Jonathan Halterman
 */
public final class FailsafeCall {
  private final FailsafeExecutor<Response> failsafe;
  private final okhttp3.Call initialCall;

  private volatile Call<Response> failsafeCall;
  private volatile CompletableFuture<Response> failsafeFuture;
  private AtomicBoolean cancelled = new AtomicBoolean();
  private AtomicBoolean executed = new AtomicBoolean();

  private FailsafeCall(okhttp3.Call call, FailsafeExecutor<Response> failsafe) {
    this.initialCall = call;
    this.failsafe = failsafe;
  }

  /**
   * Returns a FailsafeCall for the {@code call}, {@code outerPolicy} and {@code policies}. See {@link
   * Failsafe#with(Policy, Policy[])} for docs on how policy composition works.
   *
   * @param <P> policy type
   * @throws NullPointerException if {@code call} or {@code outerPolicy} are null
   */
  @SafeVarargs
  public static <P extends Policy<Response>> FailsafeCall of(okhttp3.Call call, P outerPolicy, P... policies) {
    return of(call, Failsafe.with(outerPolicy, policies));
  }

  /**
   * Returns a FailsafeCall for the {@code call} and {@code failsafeExecutor}.
   *
   * @throws NullPointerException if {@code call} or {@code failsafeExecutor} are null
   */
  public static FailsafeCall of(okhttp3.Call call, FailsafeExecutor<Response> failsafeExecutor) {
    return new FailsafeCall(Assert.notNull(call, "call"), Assert.notNull(failsafeExecutor, "failsafeExecutor"));
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
  public FailsafeCall clone() {
    return FailsafeCall.of(initialCall.clone(), failsafe);
  }

  /**
   * Executes the call until a successful response is returned or the configured policies are exceeded. To avoid leaking
   * resources callers should {@link Response#close() close} the Response which in turn will close the underlying
   * ResponseBody.
   *
   * @throws IllegalStateException if the call has already been executed
   * @throws IOException if the request could not be executed due to cancellation, a connectivity problem, or timeout
   * @throws FailsafeException if the execution fails with a checked Exception. {@link FailsafeException#getCause()} can
   * be used to learn the underlying checked exception.
   */
  public Response execute() throws IOException {
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
   * Executes the call asynchronously until a successful result is returned or the configured policies are exceeded. To
   * avoid leaking resources callers should {@link Response#close() close} the Response which in turn will close the
   * underlying ResponseBody.
   */
  public CompletableFuture<Response> executeAsync() {
    if (!executed.compareAndSet(false, true)) {
      CompletableFuture<Response> result = new CompletableFuture<>();
      result.completeExceptionally(new IllegalStateException("already executed"));
      return result;
    }

    failsafeFuture = failsafe.getAsyncExecution(exec -> {
      prepareCall(exec).enqueue(new Callback() {
        @Override
        public void onResponse(okhttp3.Call call, Response response) {
          exec.recordResult(response);
        }

        @Override
        public void onFailure(okhttp3.Call call, IOException e) {
          exec.recordException(e);
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

  private okhttp3.Call prepareCall(ExecutionContext<Response> ctx) {
    okhttp3.Call call;
    if (ctx.isFirstAttempt()) {
      call = initialCall;
    } else {
      Response response = ctx.getLastResult();
      if (response != null)
        response.close();
      call = initialCall.clone();
    }

    // Propagate cancellation to the call
    ctx.onCancel(() -> {
      cancelled.set(true);
      call.cancel();
    });
    return call;
  }
}
