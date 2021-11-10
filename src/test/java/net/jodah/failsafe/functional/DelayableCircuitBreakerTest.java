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
package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.functional.DelayableRetryPolicyTest.UncheckedExpectedException;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test
public class DelayableCircuitBreakerTest extends Testing {
  public void testUncheckedExceptionInDelayFunction() {
    CircuitBreaker<Object> breaker = CircuitBreaker.builder().withDelayFn(ctx -> {
      throw new UncheckedExpectedException();
    }).build();

    // Sync
    assertThrows(() -> Failsafe.with(breaker).run((ExecutionContext<Void> context) -> {
      throw new RuntimeException("try again");
    }), UncheckedExpectedException.class);

    // Async
    assertThrows(() -> Failsafe.with(breaker).runAsync((ExecutionContext<Void> context) -> {
      throw new RuntimeException("try again");
    }).get(1, TimeUnit.SECONDS), ExecutionException.class, UncheckedExpectedException.class);
  }

  public void shouldDelayOnMatchingResult() {
    AtomicInteger delays = new AtomicInteger();
    CircuitBreaker<Integer> breaker = CircuitBreaker.<Integer>builder()
      .handleResultIf(r -> r > 0)
      .withDelayFnWhen(ctx -> {
        delays.incrementAndGet(); // side-effect for test purposes
        return Duration.ofNanos(1);
      }, 2)
      .build();

    FailsafeExecutor<Integer> failsafe = Failsafe.with(breaker);
    failsafe.get(() -> 0);
    failsafe.get(() -> 1);
    breaker.close();
    failsafe.get(() -> 2);

    assertEquals(delays.get(), 1, "Expected a dynamic delay");
  }

  public void shouldDelayOnMatchingFailureType() {
    AtomicInteger delays = new AtomicInteger();
    CircuitBreaker<Integer> breaker = CircuitBreaker.<Integer>builder()
      .handleResultIf(r -> r > 0)
      .withDelayFnOn(ctx -> {
        delays.incrementAndGet(); // side-effect for test purposes
        return Duration.ofNanos(1);
      }, RuntimeException.class)
      .build();

    Fallback<Integer> fallback = Fallback.of(0);
    FailsafeExecutor<Integer> failsafe = Failsafe.with(fallback, breaker);
    failsafe.get(() -> 0);
    failsafe.get(() -> {
      throw new Exception();
    });
    breaker.close();
    failsafe.get(() -> {
      throw new IllegalArgumentException();
    });

    assertEquals(delays.get(), 1, "Expected a dynamic delay");
  }
}