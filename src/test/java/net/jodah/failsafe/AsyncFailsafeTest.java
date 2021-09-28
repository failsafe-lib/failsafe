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
package net.jodah.failsafe;

import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

@Test
public class AsyncFailsafeTest extends Testing {
  /**
   * Asserts that an AsyncExecution that throws is handled the same as one that properly records a failure.
   */
  public void shouldHandleThrowingAsyncExecution() {
    // Given
    AtomicInteger counter = new AtomicInteger();

    // When
    assertThrows(() -> Failsafe.with(retryTwice).getAsyncExecution(exec -> {
      counter.incrementAndGet();
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    // Then
    assertEquals(counter.get(), 3);
  }

  /**
   * Assert handles a supplier that throws instead of returning a future.
   */
  public void shouldHandleThrowingGetStageAsync() {
    // Given
    AtomicInteger counter = new AtomicInteger();

    // When
    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(() -> {
      counter.incrementAndGet();
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    // Then
    assertEquals(counter.get(), 3);

    // Given / When
    counter.set(0);
    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(context -> {
      counter.incrementAndGet();
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    // Then
    assertEquals(counter.get(), 3);
  }

  /**
   * Assert handles a supplier that throws instead of returning a future.
   */
  public void shouldHandleThrowingGetStageAsyncExecution() {
    // Given
    AtomicInteger counter = new AtomicInteger();

    // When
    assertThrows(() -> Failsafe.with(retryTwice).getStageAsyncExecution(exec -> {
      counter.incrementAndGet();
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    // Then
    assertEquals(counter.get(), 3);
  }

  /**
   * Asserts that asynchronous completion via an execution is supported. Also tests passing results through a Fallback
   * policy that should never be triggered.
   */
  public void testComplete() {
    Stats rpStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(new RetryPolicy<>().withMaxRetries(3), rpStats);
    // Passthrough policy that should allow async execution results through
    Fallback<Object> fb = Fallback.<Object>of("test").handleIf((r, f) -> false);
    Timeout<Object> timeout = Timeout.of(Duration.ofMinutes(1));
    AtomicInteger counter = new AtomicInteger();

    Consumer<FailsafeExecutor<Object>> test = failsafe -> testGetSuccess(() -> {
      counter.set(0);
      rpStats.reset();
    }, failsafe, ex -> {
      System.out.println("Executing");
      if (counter.getAndIncrement() < 3)
        throw new IllegalStateException();

      // Trigger AsyncExecution.complete() when possible
      return COMPLETE_SIGNAL;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 4);
      assertEquals(e.getExecutionCount(), 4);
      assertEquals(rpStats.failedAttemptCount, 3);
      assertEquals(rpStats.retryCount, 3);
    }, null);

    // Test RetryPolicy, Fallback
    test.accept(Failsafe.with(rp, fb));

    // Test RetryPolicy, Timeout
    rpStats.reset();
    counter.set(0);
    test.accept(Failsafe.with(rp, timeout));
  }

  interface FastServer extends Server {
  }

  @SuppressWarnings("unused")
  public void shouldSupportCovariance() {
    FastServer fastService = mock(FastServer.class);
    CompletionStage<Server> stage = Failsafe.with(new RetryPolicy<Server>()).getAsync(() -> fastService);
  }
}
