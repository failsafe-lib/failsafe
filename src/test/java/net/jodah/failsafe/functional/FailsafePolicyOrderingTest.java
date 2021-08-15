/*
 * Copyright 2021 the original author or authors.
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

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.*;
import net.jodah.failsafe.Testing.Service;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.function.ContextualSupplier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the handling of ordered policy execution.
 */
@Test
public class FailsafePolicyOrderingTest {
  private Waiter waiter;
  private CheckedSupplier<Object> failureSupplier = () -> {
    throw new IllegalStateException();
  };

  Service service = mock(Service.class);
  CheckedSupplier serviceConnect = service::connect;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  /**
   * Fallback -> RetryPolicy -> CircuitBreaker
   */
  private void assertFallbackRetryPolicyAndCircuitBreaker(boolean sync) throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);
    Fallback<Object> fb = Fallback.ofAsync(() -> "test");
    FailsafeExecutor<Object> failsafe = Failsafe.with(fb, rp, cb).onComplete(e -> {
      waiter.assertEquals(3, e.getAttemptCount());
      waiter.resume();
    });

    // When
    Object result = failsafeGet(failsafe, failureSupplier, sync);

    // Then
    waiter.await(1000);
    assertEquals(result, "test");
    assertEquals(cb.getFailureCount(), 3);
    assertEquals(cb.getSuccessCount(), 0);
    assertTrue(cb.isClosed());
  }

  public void testFallbackRetryPolicyAndCircuitBreakerSync() throws Throwable {
    assertFallbackRetryPolicyAndCircuitBreaker(true);
  }

  public void testFallbackRetryPolicyAndCircuitBreakerAsync() throws Throwable {
    assertFallbackRetryPolicyAndCircuitBreaker(false);
  }

  /**
   * CircuitBreaker -> RetryPolicy
   */
  private void assertCircuitBreakerAndRetryPolicy(boolean sync) throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);
    FailsafeExecutor<Object> failsafe = Failsafe.with(cb, rp).onComplete(e -> {
      waiter.assertEquals(3, e.getAttemptCount());
      waiter.resume();
    });

    // When
    assertFailsafeFailure(failsafe, failureSupplier, sync, IllegalStateException.class);

    // Then
    waiter.await(1000);
    assertEquals(cb.getFailureCount(), 1);
    assertEquals(cb.getSuccessCount(), 0);
    assertTrue(cb.isClosed());
  }

  public void testCircuitBreakerAndRetryPolicySync() throws Throwable {
    assertCircuitBreakerAndRetryPolicy(true);
  }

  public void testCircuitBreakerAndRetryPolicyAsync() throws Throwable {
    assertCircuitBreakerAndRetryPolicy(false);
  }

  /**
   * CircuitBreaker -> CircuitBreaker
   */
  private void assertCircuitBreakerAndCircuitBreaker(boolean sync) throws Throwable {
    // Given
    CircuitBreaker<Object> cb1 = new CircuitBreaker<>().handle(IllegalStateException.class);
    CircuitBreaker<Object> cb2 = new CircuitBreaker<>().handle(IllegalArgumentException.class);
    FailsafeExecutor<Object> failsafe = Failsafe.with(cb2, cb1).onComplete(e -> {
      waiter.assertEquals(1, e.getAttemptCount());
      waiter.resume();
    });

    // When
    assertFailsafeFailure(failsafe, failureSupplier, sync, IllegalStateException.class);

    // Then
    waiter.await(1000);
    assertEquals(cb1.getFailureCount(), 1);
    assertTrue(cb1.isOpen());
    assertEquals(cb2.getFailureCount(), 0);
    assertTrue(cb2.isClosed());

    // Given
    cb1.close();
    failsafe = Failsafe.with(cb1, cb2).onComplete(e -> {
      waiter.assertEquals(1, e.getAttemptCount());
      waiter.resume();
    });

    // When
    assertFailsafeFailure(failsafe, failureSupplier, sync, IllegalStateException.class);

    // Then
    waiter.await(1000);
    assertEquals(cb1.getFailureCount(), 1);
    assertTrue(cb1.isOpen());
    assertEquals(cb2.getFailureCount(), 0);
    assertTrue(cb2.isClosed());
  }

  public void testCircuitBreakerAndCircuitBreakerSync() throws Throwable {
    assertCircuitBreakerAndCircuitBreaker(true);
  }

  public void testCircuitBreakerAndCircuitBreakerAsync() throws Throwable {
    assertCircuitBreakerAndCircuitBreaker(false);
  }

  /**
   * Fallback -> RetryPolicy -> RetryPolicy
   */
  private void assertFallbackRetryPolicyAndRetryPolicy(boolean sync) throws Throwable {
    // Given
    AtomicInteger rp1FailedAttempts = new AtomicInteger();
    AtomicInteger rp1Failures = new AtomicInteger();
    AtomicInteger rp2FailedAttempts = new AtomicInteger();
    AtomicInteger rp2Failures = new AtomicInteger();
    RetryPolicy<Object> rp1 = new RetryPolicy<>().handle(IllegalStateException.class)
      .withMaxRetries(2)
      .onFailedAttempt(e -> rp1FailedAttempts.incrementAndGet())
      .onFailure(e -> rp1Failures.incrementAndGet());
    RetryPolicy<Object> rp2 = new RetryPolicy<>().handle(IllegalArgumentException.class)
      .withMaxRetries(3)
      .onFailedAttempt(e -> rp2FailedAttempts.incrementAndGet())
      .onFailure(e -> rp2Failures.incrementAndGet());
    Fallback<Object> fallback = Fallback.ofAsync(() -> true);
    FailsafeExecutor<Object> failsafe = Failsafe.with(fallback, rp2, rp1).onComplete(e -> {
      waiter.assertEquals(5, e.getAttemptCount());
      waiter.resume();
    });
    ContextualSupplier<Object, Object> supplier = ctx -> {
      throw ctx.getAttemptCount() % 2 == 0 ? new IllegalStateException() : new IllegalArgumentException();
    };

    // When
    Object result = failsafeGet(failsafe, supplier, sync);
    //
    // Then
    // Expected RetryPolicy failure sequence:
    //    rp1 java.lang.IllegalStateException - failure, retry
    //    rp1 java.lang.IllegalArgumentException - success
    //    rp2 java.lang.IllegalArgumentException - failure, retry
    //    rp1 java.lang.IllegalStateException - failure, retry, retries exhausted
    //    rp1 java.lang.IllegalArgumentException - success
    //    rp2 java.lang.IllegalArgumentException - failure, retry
    //    rp1 java.lang.IllegalStateException - failure, retries exceeded
    //    rp2 java.lang.IllegalStateException - success
    waiter.await(1000);
    assertEquals(result, true);
    assertEquals(rp1FailedAttempts.get(), 3);
    assertEquals(rp1Failures.get(), 1);
    assertEquals(rp2FailedAttempts.get(), 2);
    assertEquals(rp2Failures.get(), 0);

    // Given
    rp1FailedAttempts.set(0);
    rp1Failures.set(0);
    rp2FailedAttempts.set(0);
    rp2Failures.set(0);
    failsafe = Failsafe.with(fallback, rp1, rp2).onComplete(e -> {
      waiter.assertEquals(5, e.getAttemptCount());
      waiter.resume();
    });

    // When
    result = failsafeGet(failsafe, supplier, sync);

    // Then
    // Expected RetryPolicy failure sequence:
    //    rp2 java.lang.IllegalStateException - success
    //    rp1 java.lang.IllegalStateException - failure, retry
    //    rp2 java.lang.IllegalArgumentException - failure, retry
    //    rp2 java.lang.IllegalStateException - success
    //    rp1 java.lang.IllegalStateException - failure, retry, retries exhausted
    //    rp2 java.lang.IllegalArgumentException - failure, retry
    //    rp2 java.lang.IllegalStateException - success
    //    rp1 java.lang.IllegalStateException - retries exceeded
    waiter.await(1000);
    assertEquals(result, true);
    assertEquals(rp1FailedAttempts.get(), 3);
    assertEquals(rp1Failures.get(), 1);
    assertEquals(rp2FailedAttempts.get(), 2);
    assertEquals(rp2Failures.get(), 0);
  }

  public void testFallbackRetryPolicyAndRetryPolicySync() throws Throwable {
    assertFallbackRetryPolicyAndRetryPolicy(true);
  }

  public void testFallbackRetryPolicyAndRetryPolicyAsync() throws Throwable {
    assertFallbackRetryPolicyAndRetryPolicy(false);
  }

  /**
   * Tests a scenario with nested retry policies where the inner policy is exceeded and skipped.
   */
  private void assertNestedRetryPoliciesWhereInnerIsExceeded(boolean sync) throws Throwable {
    // Given
    when(service.connect()).thenThrow(failures(5, new IllegalStateException())).thenReturn(true);
    AtomicInteger rp1FailedAttempts = new AtomicInteger();
    AtomicInteger rp1Failures = new AtomicInteger();
    AtomicInteger rp2FailedAttempts = new AtomicInteger();
    AtomicInteger rp2Failures = new AtomicInteger();
    RetryPolicy<Object> rp1 = new RetryPolicy<>().handle(IllegalStateException.class)
      .withMaxRetries(1)
      .onFailedAttempt(e -> rp1FailedAttempts.incrementAndGet())
      .onFailure(e -> rp1Failures.incrementAndGet());
    RetryPolicy<Object> rp2 = new RetryPolicy<>().handle(IllegalStateException.class)
      .withMaxRetries(10)
      .onFailedAttempt(e -> rp2FailedAttempts.incrementAndGet())
      .onFailure(e -> rp2Failures.incrementAndGet());
    FailsafeExecutor<Object> failsafe = Failsafe.with(rp2, rp1).onComplete(e -> {
      waiter.assertEquals(6, e.getAttemptCount());
      waiter.resume();
    });

    // When
    Object result = failsafeGet(failsafe, serviceConnect, sync);

    // Then
    waiter.await(1000);
    assertEquals(result, true);
    assertEquals(rp1FailedAttempts.get(), 2);
    assertEquals(rp1Failures.get(), 1);
    assertEquals(rp2FailedAttempts.get(), 4);
    assertEquals(rp2Failures.get(), 0);
  }

  public void testNestedRetryPoliciesWhereInnerIsExceededSync() throws Throwable {
    assertNestedRetryPoliciesWhereInnerIsExceeded(true);
  }

  public void testNestedRetryPoliciesWhereInnerIsExceededAsync() throws Throwable {
    assertNestedRetryPoliciesWhereInnerIsExceeded(false);
  }

  @SuppressWarnings("unchecked")
  private <T> T failsafeGet(FailsafeExecutor<T> failsafe, Object supplier, boolean sync) {
    if (sync)
      return Testing.ignoreExceptions(() -> supplier instanceof CheckedSupplier ?
        failsafe.get((CheckedSupplier<T>) supplier) :
        failsafe.get((ContextualSupplier<T, T>) supplier));
    else
      return Testing.ignoreExceptions(() -> (supplier instanceof CheckedSupplier ?
        failsafe.getAsync((CheckedSupplier<T>) supplier) :
        failsafe.getAsync((ContextualSupplier<T, T>) supplier)).get());
  }

  private <T> void assertFailsafeFailure(FailsafeExecutor<T> failsafe, CheckedSupplier<T> supplier, boolean sync,
    Class<? extends Throwable> expectedException) {
    if (sync)
      Asserts.assertThrows(() -> failsafe.get(supplier), expectedException);
    else
      Asserts.assertThrows(() -> failsafe.getAsync(supplier).get(), ExecutionException.class, expectedException);
  }

  /**
   * Tests a scenario with a fallback, retry policy and nested timeouts.
   */
  private void assertFallbackRetryPolicyAndNestedTimeous(boolean sync) throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    AtomicInteger timeout1Failures = new AtomicInteger();
    AtomicInteger timeout2Failures = new AtomicInteger();
    Timeout<Object> timeout1 = Timeout.of(Duration.ofMillis(50)).onFailure(e -> timeout1Failures.incrementAndGet());
    Timeout<Object> timeout2 = Timeout.of(Duration.ofMillis(100)).onFailure(e -> timeout2Failures.incrementAndGet());
    Fallback<Object> fallback = Fallback.of(true);
    FailsafeExecutor<Object> failsafe = Failsafe.with(fallback, rp, timeout1, timeout2).onComplete(e -> {
      waiter.assertEquals(3, e.getAttemptCount());
      waiter.assertEquals(e.getResult(), true);
      waiter.assertNull(e.getFailure());
      waiter.resume();
    });
    CheckedSupplier supplier = () -> {
      Thread.sleep(200);
      return null;
    };

    // When
    Object result = failsafeGet(failsafe, supplier, sync);

    // Then
    waiter.await(1000);
    assertEquals(result, true);
    assertEquals(timeout1Failures.get(), 3);
    assertEquals(timeout2Failures.get(), 3);
  }

  public void testFallbackRetryPolicyAndNestedTimeousSync() throws Throwable {
    assertFallbackRetryPolicyAndNestedTimeous(true);
  }

  public void testFallbackRetryPolicyAndNestedTimeousAsync() throws Throwable {
    assertFallbackRetryPolicyAndNestedTimeous(true);
  }
}
