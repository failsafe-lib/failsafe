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
import net.jodah.failsafe.RetryPolicyBuilder;
import net.jodah.failsafe.testing.Testing;
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
  RetryPolicyBuilder<Boolean> rpBuilder = RetryPolicy.<Boolean>builder()
    .handleResultIf(r -> r == null || !r)
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
      Failsafe.with(rpBuilder.onFailedAttempt(e -> failedAttempts.incrementAndGet())
        .onRetry(e -> retries.incrementAndGet())
        .build()).get(() -> {
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
  public void failedAttemptListener_WithFailedResponses_ShouldBeCalled() {
    AtomicInteger listenerCallbacks = new AtomicInteger();
    RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
      .handleResultIf(response -> response != null && !response)
      .handle(Exception.class)
      .withMaxRetries(3)
      .onFailedAttempt(e -> listenerCallbacks.incrementAndGet())
      .build();
    Failsafe.with(policy).get(() -> false);
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithFailedResponses_ShouldBeCalled() {
    AtomicInteger listenerCallbacks = new AtomicInteger();
    RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
      .handleResultIf(response -> response != null && !response)
      .handle(Exception.class)
      .withMaxRetries(3)
      .onRetry(e -> listenerCallbacks.incrementAndGet())
      .build();
    Failsafe.with(policy).get(() -> false);
    assertEquals(listenerCallbacks.get(), 3);
  }

  @Test
  public void failedAttemptListener_WithExceptions_ShouldBeCalled() {
    AtomicInteger listenerCallbacks = new AtomicInteger();
    RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
      .handleResultIf(response -> response != null && !response)
      .handle(Exception.class)
      .withMaxRetries(3)
      .onFailedAttempt(e -> listenerCallbacks.incrementAndGet())
      .build();
    Testing.ignoreExceptions(() -> Failsafe.with(policy).get(() -> {
      throw new RuntimeException();
    }));
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithExceptions_ShouldBeCalled() {
    AtomicInteger listenerCallbacks = new AtomicInteger();
    RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
      .handleResultIf(response -> response != null && !response)
      .handle(Exception.class)
      .withMaxRetries(3)
      .onRetry(e -> listenerCallbacks.incrementAndGet())
      .build();
    Testing.ignoreExceptions(() -> Failsafe.with(policy).get(() -> {
      throw new RuntimeException();
    }));
    assertEquals(listenerCallbacks.get(), 3);
  }
}
