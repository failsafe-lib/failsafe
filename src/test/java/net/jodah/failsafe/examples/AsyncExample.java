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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

public class AsyncExample {
  static ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  static RetryPolicy retryPolicy = new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS).withJitter(.25);
  static Service service = new Service();

  public static class Service {
    AtomicInteger failures = new AtomicInteger();

    // Fail 3 times then succeed
    CompletableFuture<Boolean> connect() {
      CompletableFuture<Boolean> future = new CompletableFuture<>();
      executor.submit(() -> {
        if (failures.getAndIncrement() < 3)
          future.completeExceptionally(new RuntimeException());
        else
          future.complete(true);
      });
      return future;
    }
  }

  public static void main(String... args) throws Throwable {
    Failsafe.with(retryPolicy)
        .with(executor)
        .getAsyncExecution(execution -> service.connect().whenComplete((result, failure) -> {
          if (execution.complete(result, failure))
            System.out.println("Success");
          else if (!execution.retry())
            System.out.println("Connection attempts failed");
        }));

    Thread.sleep(3000);
  }
}
