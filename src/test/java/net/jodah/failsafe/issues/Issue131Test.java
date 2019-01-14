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
package net.jodah.failsafe.issues;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

@Test
public class Issue131Test {

  /**
   * This predicate is invoked in failure scenarios with an arg of null,
   * producing a {@link NullPointerException} yielding surpising results.
   */
  private static Predicate<String> handleIfEqualsIgnoreCaseFoo = s -> {
    return s.equalsIgnoreCase("foo"); // produces NPE when invoked in failing scenarios.
  };

  /**
   * Simple synchronous case throwing a {@link NullPointerException}
   * instead of the expected {@link FailsafeException}.
   */
  @Test(expectedExceptions = FailsafeException.class)
  public void syncShouldThrowTheUnderlyingIOException() {
    CircuitBreaker<String> circuitBreaker = new CircuitBreaker<String>().handleResultIf(handleIfEqualsIgnoreCaseFoo);
    FailsafeExecutor<String> failsafe = Failsafe.with(circuitBreaker);

    // I expect this getAsync() to throw IOException, not NPE.
    failsafe.get(() -> {
      throw new IOException("let's blame it on network error");
    });
  }


  /**
   * More alarming async case where the Future is not even completed
   * since Failsafe does not recover from the {@link NullPointerException} thrown by the predicate.
   */
  public void asyncShouldCompleteTheFuture() throws Throwable {
    CircuitBreaker<String> circuitBreaker = new CircuitBreaker<String>().handleResultIf(handleIfEqualsIgnoreCaseFoo);
    FailsafeExecutor<String> failsafe = Failsafe.with(circuitBreaker).with(Executors.newSingleThreadScheduledExecutor());

    Waiter waiter = new Waiter();

    failsafe
      .futureAsync(() -> {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new IOException("let's blame it on network error"));
        return future;
      })
      .whenComplete((s, t) -> waiter.resume()); // Never invoked!

    waiter.await(1000);
  }
}
