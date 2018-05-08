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
import net.jodah.failsafe.*;
import net.jodah.failsafe.function.Predicate;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.*;

import static org.testng.Assert.assertFalse;

@Test
public class Issue131Test {

  /**
   * This predicate is invoked in failure scenarios with an arg of null,
   * producing a {@link NullPointerException} yielding surpising results.
   */
  private static Predicate<String> failIfEqualsIgnoreCaseFoo = new Predicate<String>() {
    @Override
    public boolean test(String s) {
      return s.equalsIgnoreCase("foo"); // produces NPE when invoked in failing scenarios.
    }
  };

  /**
   * Simple synchronous case throwing a {@link NullPointerException}
   * instead of the expected {@link FailsafeException}.
   */
  @Test(expectedExceptions = FailsafeException.class)
  public void syncShouldThrowTheUnderlyingIOException() throws Throwable {
    CircuitBreaker circuitBreaker = new CircuitBreaker().failIf(failIfEqualsIgnoreCaseFoo);
    SyncFailsafe<String> failsafe = Failsafe.<String>with(circuitBreaker);

    // I expect this get() to throw IOException, not NPE.
    failsafe.get(new Callable<String>() {
      @Override
      public String call() throws Exception {
        throw new IOException("let's blame it on network error");
      }
    });
  }


  /**
   * More alarming async case where the Future is not even completed
   * since Failsafe does not recover from the {@link NullPointerException} thrown by the predicate.
   */
  public void asyncShouldCompleteTheFuture() throws Throwable {
    CircuitBreaker circuitBreaker = new CircuitBreaker().failIf(failIfEqualsIgnoreCaseFoo);
    AsyncFailsafe<String> failsafe = Failsafe.<String>with(circuitBreaker).with(Executors.newSingleThreadScheduledExecutor());

    Waiter waiter = new Waiter();

    failsafe
      .future(new Callable<CompletionStage<String>>() {
        @Override
        public CompletionStage<String> call() throws Exception {
          CompletableFuture<String> future = new CompletableFuture<String>();
          future.completeExceptionally(new IOException("let's blame it on network error"));
          return future;
        }
      })
      .whenComplete((s, t) -> waiter.resume()); // Never invoked!

    waiter.await(1000);
  }
}
