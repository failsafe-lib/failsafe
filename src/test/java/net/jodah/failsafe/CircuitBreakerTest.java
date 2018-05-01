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

import static net.jodah.failsafe.Asserts.assertThrows;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.function.BiPredicate;
import org.testng.annotations.Test;

@Test
public class CircuitBreakerTest {
  public void testIsFailureForNull() {
    CircuitBreaker breaker = new CircuitBreaker();
    assertFalse(breaker.isFailure(null, null));
  }

  public void testIsFailureForFailurePredicate() {
    CircuitBreaker breaker = new CircuitBreaker().failOn(failure -> failure instanceof ConnectException);
    assertTrue(breaker.isFailure(null, new ConnectException()));
    assertFalse(breaker.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureForResultPredicate() {
    CircuitBreaker breaker = new CircuitBreaker().failIf((Integer result) -> result > 100);
    assertTrue(breaker.isFailure(110, null));
    assertFalse(breaker.isFailure(50, null));
  }

  public void testIgnoresThrowingPredicate() {
    CircuitBreaker breaker = new CircuitBreaker().failIf(new BiPredicate<Integer, Throwable>() {
      @Override
      public boolean test(Integer integer, Throwable throwable) {
        throw new NullPointerException();
      }
    });
    assertFalse(breaker.isFailure(1, null));
  }

  @Test(expectedExceptions = OutOfMemoryError.class)
  public void testThrowsFatalErrors() {
    CircuitBreaker breaker = new CircuitBreaker().failIf(new BiPredicate<String, Throwable>() {
      @Override
      public boolean test(String integer, Throwable throwable) {
        throw new OutOfMemoryError();
      }
    });
    breaker.isFailure("result", null);
  }

  @SuppressWarnings("unchecked")
  public void testIsFailureForFailure() {
    CircuitBreaker breaker = new CircuitBreaker();
    assertTrue(breaker.isFailure(null, new Exception()));
    assertTrue(breaker.isFailure(null, new IllegalArgumentException()));

    breaker = new CircuitBreaker().failOn(Exception.class);
    assertTrue(breaker.isFailure(null, new Exception()));
    assertTrue(breaker.isFailure(null, new IllegalArgumentException()));

    breaker = new CircuitBreaker().failOn(IllegalArgumentException.class, IOException.class);
    assertTrue(breaker.isFailure(null, new IllegalArgumentException()));
    assertTrue(breaker.isFailure(null, new IOException()));
    assertFalse(breaker.isFailure(null, new RuntimeException()));
    assertFalse(breaker.isFailure(null, new IllegalStateException()));

    breaker = new CircuitBreaker().failOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(breaker.isFailure(null, new IllegalArgumentException()));
    assertFalse(breaker.isFailure(null, new RuntimeException()));
    assertFalse(breaker.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureForResult() {
    CircuitBreaker breaker = new CircuitBreaker().failWhen(10);
    assertTrue(breaker.isFailure(10, null));
    assertFalse(breaker.isFailure(5, null));
  }

  public void shouldRequireValidDelay() {
    assertThrows(() -> new CircuitBreaker().withDelay(5, null), NullPointerException.class);
    assertThrows(() -> new CircuitBreaker().withDelay(-1, TimeUnit.MILLISECONDS), IllegalArgumentException.class);
  }

  public void shouldRequireValidTimeout() {
    assertThrows(() -> new CircuitBreaker().withTimeout(5, null), NullPointerException.class);
    assertThrows(() -> new CircuitBreaker().withTimeout(-1, TimeUnit.MILLISECONDS), IllegalArgumentException.class);
  }

  public void shouldRequireValidFailureThreshold() {
    assertThrows(() -> new CircuitBreaker().withFailureThreshold(0), IllegalArgumentException.class);
  }

  public void shouldRequireValidFailureThresholdRatio() {
    assertThrows(() -> new CircuitBreaker().withFailureThreshold(0, 2), IllegalArgumentException.class);
    assertThrows(() -> new CircuitBreaker().withFailureThreshold(2, 0), IllegalArgumentException.class);
    assertThrows(() -> new CircuitBreaker().withFailureThreshold(2, 1), IllegalArgumentException.class);
  }

  public void shouldRequireValidSuccessThreshold() {
    assertThrows(() -> new CircuitBreaker().withSuccessThreshold(0), IllegalArgumentException.class);
  }

  public void shouldRequireValidSuccessThresholdRatio() {
    assertThrows(() -> new CircuitBreaker().withSuccessThreshold(0, 2), IllegalArgumentException.class);
    assertThrows(() -> new CircuitBreaker().withSuccessThreshold(2, 0), IllegalArgumentException.class);
    assertThrows(() -> new CircuitBreaker().withSuccessThreshold(2, 1), IllegalArgumentException.class);
  }
}
