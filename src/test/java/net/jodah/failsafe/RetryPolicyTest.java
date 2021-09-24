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

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.testng.Assert.*;

@Test
public class RetryPolicyTest {
  public void testIsFailureNull() {
    RetryPolicy<Object> policy = new RetryPolicy<>();
    assertFalse(policy.isFailure(null, null));
  }

  public void testIsFailureCompletionPredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().handleIf(
      (result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.isFailure("test", null));
    // No retries needed for successful result
    assertFalse(policy.isFailure(0, null));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureFailurePredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().handleIf(failure -> failure instanceof ConnectException);
    assertTrue(policy.isFailure(null, new ConnectException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureResultPredicate() {
    RetryPolicy<Integer> policy = new RetryPolicy<Integer>().handleResultIf(result -> result > 100);
    assertTrue(policy.isFailure(110, null));
    assertFalse(policy.isFailure(50, null));
  }

  public void testIsFailureFailure() {
    RetryPolicy<Object> policy = new RetryPolicy<>();
    assertTrue(policy.isFailure(null, new Exception()));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));

    policy = new RetryPolicy<>().handle(Exception.class);
    assertTrue(policy.isFailure(null, new Exception()));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));

    policy = new RetryPolicy<>().handle(RuntimeException.class);
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new Exception()));

    policy = new RetryPolicy<>().handle(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertTrue(policy.isFailure(null, new IOException()));
    assertFalse(policy.isFailure(null, new RuntimeException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));

    policy = new RetryPolicy<>().handle(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new RuntimeException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureResult() {
    RetryPolicy<Object> policy = new RetryPolicy<>().handleResult(10);
    assertTrue(policy.isFailure(10, null));
    assertFalse(policy.isFailure(5, null));
    assertTrue(policy.isFailure(5, new Exception()));
  }

  public void testIsAbortableNull() {
    RetryPolicy<Object> policy = new RetryPolicy<>();
    assertFalse(policy.isAbortable(null, null));
  }

  public void testIsAbortableCompletionPredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().abortIf(
      (result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.isAbortable("test", null));
    assertFalse(policy.isAbortable(0, null));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testIsAbortableFailurePredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().abortOn(failure -> failure instanceof ConnectException);
    assertTrue(policy.isAbortable(null, new ConnectException()));
    assertFalse(policy.isAbortable(null, new IllegalArgumentException()));
  }

  public void testIsAbortablePredicate() {
    RetryPolicy<Integer> policy = new RetryPolicy<Integer>().abortIf(result -> result > 100);
    assertTrue(policy.isAbortable(110, null));
    assertFalse(policy.isAbortable(50, null));
    assertFalse(policy.isAbortable(50, new IllegalArgumentException()));
  }

  @SuppressWarnings("unchecked")
  public void testIsAbortableFailure() {
    RetryPolicy policy = new RetryPolicy<>().abortOn(Exception.class);
    assertTrue(policy.isAbortable(null, new Exception()));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));

    policy = new RetryPolicy<>().abortOn(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertTrue(policy.isAbortable(null, new IOException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));

    policy = new RetryPolicy<>().abortOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testIsAbortableResult() {
    RetryPolicy<Object> policy = new RetryPolicy<>().abortWhen(10);
    assertTrue(policy.isAbortable(10, null));
    assertFalse(policy.isAbortable(5, null));
    assertFalse(policy.isAbortable(5, new IllegalArgumentException()));
  }

  public void shouldRequireValidBackoff() {
    Asserts.assertThrows(() -> new RetryPolicy<>().withBackoff(0, 0, null), NullPointerException.class);
    Asserts.assertThrows(
      () -> new RetryPolicy<>().withMaxDuration(Duration.ofMillis(1)).withBackoff(100, 120, ChronoUnit.MILLIS),
      IllegalStateException.class);
    Asserts.assertThrows(() -> new RetryPolicy<>().withBackoff(-3, 10, ChronoUnit.MILLIS),
      IllegalArgumentException.class);
    Asserts.assertThrows(() -> new RetryPolicy<>().withBackoff(100, 10, ChronoUnit.MILLIS),
      IllegalArgumentException.class);
    Asserts.assertThrows(() -> new RetryPolicy<>().withBackoff(5, 10, ChronoUnit.MILLIS, .5),
      IllegalArgumentException.class);
  }

  public void shouldRequireValidDelay() {
    Asserts.assertThrows(() -> new RetryPolicy<>().withDelay((Duration) null), NullPointerException.class);
    Asserts.assertThrows(
      () -> new RetryPolicy<>().withMaxDuration(Duration.ofMillis(1)).withDelay(Duration.ofMillis(100)),
      IllegalStateException.class);
    Asserts.assertThrows(
      () -> new RetryPolicy<>().withBackoff(1, 2, ChronoUnit.MILLIS).withDelay(Duration.ofMillis(100)),
      IllegalStateException.class);
    Asserts.assertThrows(() -> new RetryPolicy<>().withDelay(Duration.ofMillis(-1)), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxRetries() {
    Asserts.assertThrows(() -> new RetryPolicy<>().withMaxRetries(-4), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxDuration() {
    Asserts.assertThrows(
      () -> new RetryPolicy<>().withDelay(Duration.ofMillis(100)).withMaxDuration(Duration.ofMillis(100)),
      IllegalStateException.class);
  }

  public void shouldSupportRandomDelay() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withDelay(1, 10, ChronoUnit.NANOS);
    assertEquals(rp.getDelayMin().toNanos(), 1);
    assertEquals(rp.getDelayMax().toNanos(), 10);
  }

  public void testGetMaxAttempts() {
    assertEquals(new RetryPolicy<>().withMaxRetries(-1).getMaxAttempts(), -1);
    assertEquals(new RetryPolicy<>().withMaxRetries(0).getMaxAttempts(), 1);
    assertEquals(new RetryPolicy<>().withMaxRetries(1).getMaxAttempts(), 2);
  }

  public void testCopy() {
    RetryPolicy<Object> rp = new RetryPolicy<>();
    rp.withBackoff(Duration.ofSeconds(2), Duration.ofSeconds(20), 2.5);
    rp.withMaxDuration(Duration.ofSeconds(60));
    rp.withMaxRetries(3);
    rp.onFailure(event -> System.out.println("Failed"));
    rp.onSuccess(event -> System.out.println("Success"));

    RetryPolicy<Object> rp2 = rp.copy();
    assertEquals(rp2.getDelay().toNanos(), rp.getDelay().toNanos());
    assertEquals(rp2.getDelayFactor(), rp.getDelayFactor());
    assertEquals(rp2.getMaxDelay().toNanos(), rp.getMaxDelay().toNanos());
    assertEquals(rp2.getMaxDuration().toNanos(), rp.getMaxDuration().toNanos());
    assertEquals(rp2.getMaxRetries(), rp.getMaxRetries());
    //    assertEquals(rp2.failureListener, rp.failureListener);
    //    assertEquals(rp2.successListener, rp.successListener);
  }
}
