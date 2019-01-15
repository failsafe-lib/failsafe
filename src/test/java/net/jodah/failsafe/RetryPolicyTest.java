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
  void shouldFail(Runnable runnable, Class<? extends Exception> expected) {
    try {
      runnable.run();
      fail("A failure was expected");
    } catch (Exception e) {
      assertTrue(e.getClass().isAssignableFrom(expected), "The expected exception was not of the expected type " + e);
    }
  }

  public void testisFailureNull() {
    RetryPolicy<Object> policy = new RetryPolicy<>();
    assertFalse(policy.isFailure(null, null));
  }

  public void testisFailureCompletionPredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>()
        .handleIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.isFailure("test", null));
    // No retries needed for successful result
    assertFalse(policy.isFailure(0, null));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testisFailureFailurePredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().handleIf(failure -> failure instanceof ConnectException);
    assertTrue(policy.isFailure(null, new ConnectException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testisFailureResultPredicate() {
    RetryPolicy<Integer> policy = new RetryPolicy<Integer>().handleResultIf(result -> result > 100);
    assertTrue(policy.isFailure(110, null));
    assertFalse(policy.isFailure(50, null));
  }

  @SuppressWarnings("unchecked")
  public void testisFailureFailure() {
    RetryPolicy policy = new RetryPolicy();
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

  public void testisFailureResult() {
    RetryPolicy<Object> policy = new RetryPolicy<>().handleResult(10);
    assertTrue(policy.isFailure(10, null));
    assertFalse(policy.isFailure(5, null));
    assertTrue(policy.isFailure(5, new Exception()));
  }

  public void testCanAbortForNull() {
    RetryPolicy<Object> policy = new RetryPolicy<>();
    assertFalse(policy.isAbortable(null, null));
  }

  public void testCanAbortForCompletionPredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>()
        .abortIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.isAbortable("test", null));
    assertFalse(policy.isAbortable(0, null));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testCanAbortForFailurePredicate() {
    RetryPolicy<Object> policy = new RetryPolicy<>().abortOn(failure -> failure instanceof ConnectException);
    assertTrue(policy.isAbortable(null, new ConnectException()));
    assertFalse(policy.isAbortable(null, new IllegalArgumentException()));
  }

  public void testCanAbortForResultPredicate() {
    RetryPolicy<Integer> policy = new RetryPolicy<Integer>().abortIf(result -> result > 100);
    assertTrue(policy.isAbortable(110, null));
    assertFalse(policy.isAbortable(50, null));
    assertFalse(policy.isAbortable(50, new IllegalArgumentException()));
  }

  @SuppressWarnings("unchecked")
  public void testCanAbortForFailure() {
    RetryPolicy policy = new RetryPolicy().abortOn(Exception.class);
    assertTrue(policy.isAbortable(null, new Exception()));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));

    policy = new RetryPolicy().abortOn(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertTrue(policy.isAbortable(null, new IOException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));

    policy = new RetryPolicy().abortOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testCanAbortForResult() {
    RetryPolicy<Object> policy = new RetryPolicy<>().abortWhen(10);
    assertTrue(policy.isAbortable(10, null));
    assertFalse(policy.isAbortable(5, null));
    assertFalse(policy.isAbortable(5, new IllegalArgumentException()));
  }

  public void testWithDelayFunction() {
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>();
    assertTrue(retryPolicy.canApplyDelayFn("expected", new IllegalArgumentException()));
    retryPolicy.withDelay((r, f, ctx) -> Duration.ofMillis(10));
    assertTrue(retryPolicy.canApplyDelayFn("expected", new IllegalArgumentException()));
  }

  public void testWithDelayOn() {
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelayOn((r, f, ctx) -> Duration.ofMillis(10),
        IllegalStateException.class);
    assertTrue(retryPolicy.canApplyDelayFn("foo", new IllegalStateException()));
    assertFalse(retryPolicy.canApplyDelayFn("foo", null));
    assertFalse(retryPolicy.canApplyDelayFn("foo", new IllegalArgumentException()));
  }

  public void testWithDelayWhen() {
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelayWhen((r, f, ctx) -> Duration.ofMillis(10),
        "expected");
    assertTrue(retryPolicy.canApplyDelayFn("expected", new IllegalStateException()));
    assertFalse(retryPolicy.canApplyDelayFn(null, new IllegalStateException()));
    assertFalse(retryPolicy.canApplyDelayFn("not expected", new IllegalStateException()));
  }

  public void shouldRequireValidBackoff() {
    shouldFail(() -> new RetryPolicy().withBackoff(0, 0, null), NullPointerException.class);
    shouldFail(
        () -> new RetryPolicy().withMaxDuration(Duration.ofMillis(1)).withBackoff(100, 120, ChronoUnit.MILLIS),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(-3, 10, ChronoUnit.MILLIS), IllegalArgumentException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(100, 10, ChronoUnit.MILLIS), IllegalArgumentException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(5, 10, ChronoUnit.MILLIS, .5), IllegalArgumentException.class);
  }

  public void shouldRequireValidDelay() {
    shouldFail(() -> new RetryPolicy().withDelay((Duration)null), NullPointerException.class);
    shouldFail(() -> new RetryPolicy().withMaxDuration(Duration.ofMillis(1)).withDelay(Duration.ofMillis(100)),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(1, 2, ChronoUnit.MILLIS).withDelay(Duration.ofMillis(100)),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withDelay(Duration.ofMillis(-1)), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxRetries() {
    shouldFail(() -> new RetryPolicy().withMaxRetries(-4), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxDuration() {
    shouldFail(
        () -> new RetryPolicy().withDelay(Duration.ofMillis(100)).withMaxDuration(Duration.ofMillis(100)),
        IllegalStateException.class);
  }

  public void testCopy() {
    RetryPolicy rp = new RetryPolicy();
    rp.withBackoff(2, 20, ChronoUnit.SECONDS, 2.5);
    rp.withMaxDuration(Duration.ofSeconds(60));
    rp.withMaxRetries(3);

    RetryPolicy rp2 = rp.copy();
    assertEquals(rp.getDelay().toNanos(), rp2.getDelay().toNanos());
    assertEquals(rp.getDelayFactor(), rp2.getDelayFactor());
    assertEquals(rp.getMaxDelay().toNanos(), rp2.getMaxDelay().toNanos());
    assertEquals(rp.getMaxDuration().toNanos(), rp2.getMaxDuration().toNanos());
    assertEquals(rp.getMaxRetries(), rp2.getMaxRetries());
  }
}
