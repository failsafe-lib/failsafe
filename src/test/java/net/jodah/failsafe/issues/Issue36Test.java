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

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * https://github.com/jhalterman/failsafe/issues/36
 */
@Test
public class Issue36Test {
  RetryPolicy retryPolicy = new RetryPolicy().handleResultIf((Boolean r) -> r == null || !r)
      .handle(Exception.class)
      .withMaxRetries(3);
  AtomicInteger calls;
  AtomicInteger failedAttempts;
  AtomicInteger retries;

  @BeforeMethod
  protected void beforeMethod() {
    calls = new AtomicInteger();
    failedAttempts = new AtomicInteger();
    retries = new AtomicInteger();
  }

  public void test() {
    try {
      Failsafe.with(retryPolicy)
          .onFailedAttempt((r, f, c) -> failedAttempts.incrementAndGet())
          .onRetry(e -> retries.incrementAndGet())
          .get(() -> {
            calls.incrementAndGet();
            throw new Exception();
          });
      fail();
    } catch (Exception expected) {
    }

    // Then
    assertCounters();
  }

  void assertCounters() {
    assertEquals(calls.get(), 4);
    assertEquals(failedAttempts.get(), 4);
    assertEquals(retries.get(), 3);
  }

  @Test
  public void failedAttemptListener_WithFailedResponses_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().handleResultIf((Boolean response) -> response != null && !response)
        .handle(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    Failsafe.<Boolean>with(policy)
        .onFailedAttempt((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
        .get(() -> false);
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithFailedResponses_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().handleResultIf((Boolean response) -> response != null && !response)
        .handle(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    Failsafe.<Boolean>with(policy)
        .onRetry((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
        .get(() -> false);
    assertEquals(listenerCallbacks.get(), 3);
  }

  @Test
  public void failedAttemptListener_WithExceptions_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().handleResultIf((Boolean response) -> response != null && !response)
        .handle(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    try {
      Failsafe.<Boolean>with(policy)
          .onFailedAttempt((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
          .get(() -> {
            throw new RuntimeException();
          });
    } catch (RuntimeException e) {
    }
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithExceptions_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().handleResultIf((Boolean response) -> response != null && !response)
        .handle(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    try {
      Failsafe.<Boolean>with(policy)
          .onRetry((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
          .get(() -> {
            throw new RuntimeException();
          });
    } catch (RuntimeException e) {
    }
    assertEquals(listenerCallbacks.get(), 3);
  }
}
