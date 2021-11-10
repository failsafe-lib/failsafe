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

import dev.failsafe.Fallback;
import dev.failsafe.testing.Testing;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests various Fallback scenarios.
 */
@Test
public class FallbackTest extends Testing {
  /**
   * Tests a simple execution that does not fallback.
   */
  public void shouldNotFallback() {
    testGetSuccess(Failsafe.with(Fallback.of(true)), ctx -> {
      return false;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, false);
  }

  /**
   * Tests the handling of a fallback with no conditions.
   */
  public void testFallbackWithoutConditions() {
    // Given
    Fallback<Object> fallback = Fallback.of(true);

    // When / Then
    testRunSuccess(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, true);

    // Given
    RetryPolicy<Object> retryPolicy = RetryPolicy.ofDefaults();

    // When / Then
    testRunSuccess(Failsafe.with(fallback, retryPolicy), ctx -> {
      throw new IllegalStateException();
    }, true);
  }

  /**
   * Tests the handling of a fallback with conditions.
   */
  public void testFallbackWithConditions() {
    // Given
    Fallback<Boolean> fallback = Fallback.builder(true).handle(IllegalArgumentException.class).build();

    // When / Then
    testRunFailure(Failsafe.with(fallback), ctx -> {
      throw new IllegalStateException();
    }, IllegalStateException.class);

    testRunSuccess(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, true);
  }

  /**
   * Tests Fallback.ofException.
   */
  public void shouldFallbackOfException() {
    // Given
    Fallback<Object> fallback = Fallback.ofException(e -> new IllegalStateException(e.getLastFailure()));

    // When / Then
    testRunFailure(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, IllegalStateException.class);
  }
}
