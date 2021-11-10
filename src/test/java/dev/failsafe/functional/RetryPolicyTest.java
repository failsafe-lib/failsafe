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

import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;

@Test
public class RetryPolicyTest extends Testing {
  /**
   * Tests a simple execution that does not retry.
   */
  public void shouldNotRetry() {
    testGetSuccess(Failsafe.with(RetryPolicy.ofDefaults()), ctx -> {
      return true;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, true);
  }

  /**
   * Asserts that a non-handled exception does not trigger retries.
   */
  public void shouldThrowOnNonRetriableFailure() {
    // Given
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
      .withMaxRetries(-1)
      .handle(IllegalStateException.class)
      .build();

    // When / Then
    testRunFailure(Failsafe.with(retryPolicy), ctx -> {
      if (ctx.getAttemptCount() < 2)
        throw new IllegalStateException();
      throw new IllegalArgumentException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
    }, IllegalArgumentException.class);
  }

  /**
   * Asserts that an execution is failed when the max duration is exceeded.
   */
  public void shouldCompleteWhenMaxDurationExceeded() {
    Stats stats = new Stats();
    RetryPolicy<Boolean> retryPolicy = withStats(
      RetryPolicy.<Boolean>builder().handleResult(false).withMaxDuration(Duration.ofMillis(100)), stats).build();

    testGetSuccess(() -> {
      stats.reset();
    }, Failsafe.with(retryPolicy), ctx -> {
      Testing.sleep(120);
      return false;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(stats.failureCount, 1);
    }, false);
  }

  /**
   * Asserts that the ExecutionScheduledEvent.getDelay is as expected.
   */
  public void assertScheduledRetryDelay() throws Throwable {
    // Given
    Waiter waiter = new Waiter();
    RetryPolicy<Object> rp = RetryPolicy.builder().withDelay(Duration.ofMillis(10)).onRetryScheduled(e -> {
      waiter.assertEquals(e.getDelay().toMillis(), 10L);
      waiter.resume();
    }).build();

    // Sync when / then
    ignoreExceptions(() -> Failsafe.with(rp).run(() -> {
      throw new IllegalStateException();
    }));
    waiter.await(1000);

    // Async when / then
    Failsafe.with(rp).runAsync(() -> {
      throw new IllegalStateException();
    });
    waiter.await(1000);
  }
}
