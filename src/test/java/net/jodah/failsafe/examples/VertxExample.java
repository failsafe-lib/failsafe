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
package net.jodah.failsafe.examples;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.util.concurrent.DefaultScheduledFuture;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class VertxExample {
  static Vertx vertx = Vertx.vertx();

  /** Create RetryPolicy to handle Vert.x failures */
  static RetryPolicy<Message<?>> retryPolicy = new RetryPolicy<Message<?>>().handleIf(
      (ReplyException failure) -> ReplyFailure.RECIPIENT_FAILURE.equals(failure.failureType())
        || ReplyFailure.TIMEOUT.equals(failure.failureType()))
    .onRetry(e -> System.out.println("Received failed reply. Retrying."))
    .onSuccess(e -> System.out.println("Received reply " + e.getResult().body()))
    .onFailure(e -> System.out.println("Execution and retries failed"));

  /** Adapt Vert.x timer to a Failsafe Scheduler */
  static Scheduler scheduler = (callable, delay, unit) -> {
    Runnable runnable = () -> {
      try {
        callable.call();
      } catch (Exception ignore) {
      }
    };
    return new DefaultScheduledFuture<Object>() {
      long timerId;

      {
        if (delay == 0)
          vertx.getOrCreateContext().runOnContext(e -> runnable.run());
        else
          timerId = vertx.setTimer(unit.toMillis(delay), tid -> runnable.run());
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return delay != 0 && vertx.cancelTimer(timerId);
      }
    };
  };

  /**
   * A Vert.x sender and retryable receiver example.
   */
  public static void main(String... args) throws Throwable {
    // Receiver that fails 2 times then succeeds
    AtomicInteger failures = new AtomicInteger();
    vertx.eventBus().consumer("ping-address", message -> {
      if (failures.getAndIncrement() < 2)
        message.fail(1, "Failed");
      else {
        message.reply("pong!");
      }
    });

    // Retryable sender
    Failsafe.with(retryPolicy.copy().withDelay(Duration.ofSeconds(1)))
      .with(scheduler)
      .getAsyncExecution(execution -> vertx.eventBus().send("ping-address", "ping!", reply -> {
        if (reply.succeeded())
          execution.recordResult(reply.result());
        else
          execution.recordFailure(reply.cause());
      }));

    Thread.sleep(5000);
    System.exit(0);
  }
}
