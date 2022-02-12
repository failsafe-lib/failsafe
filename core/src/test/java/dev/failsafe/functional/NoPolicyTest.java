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
package dev.failsafe.functional;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Tests the use of Failsafe.none().
 */
@Test
public class NoPolicyTest extends Testing {
  public void testWithNoPolicy() {
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger failureCounter = new AtomicInteger();
    FailsafeExecutor<Object> failsafe = Failsafe.none().onFailure(e -> {
      System.out.println("Failure");
      failureCounter.incrementAndGet();
    }).onSuccess(e -> {
      System.out.println("Success");
      successCounter.incrementAndGet();
    });

    // Test success
    testGetSuccess(() -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, failsafe, ctx -> {
      return "success";
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 1);
      assertEquals(failureCounter.get(), 0);
    }, "success");

    // Test failure
    testRunFailure(() -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, failsafe, ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 0);
      assertEquals(failureCounter.get(), 1);
    }, IllegalStateException.class);
  }
}
