package net.jodah.recurrent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

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

  public void testAllowsRetriesForNull() {
    RetryPolicy policy = new RetryPolicy();
    assertFalse(policy.allowsRetriesFor(null, null));
  }

  public void testAllowsRetriesForCompletionPredicate() {
    RetryPolicy policy = new RetryPolicy()
        .retryIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.allowsRetriesFor("test", null));
    // No retries needed for successful result
    assertFalse(policy.allowsRetriesFor(0, null));
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testAllowsRetriesForFailurePredicate() {
    RetryPolicy policy = new RetryPolicy().retryOn(failure -> failure instanceof ConnectException);
    assertTrue(policy.allowsRetriesFor(null, new ConnectException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testAllowsRetriesForResultPredicate() {
    RetryPolicy policy = new RetryPolicy().retryIf((Integer result) -> result > 100);
    assertTrue(policy.allowsRetriesFor(110, null));
    assertFalse(policy.allowsRetriesFor(50, null));
  }

  @SuppressWarnings("unchecked")
  public void testAllowsRetriesForFailure() {
    RetryPolicy policy = new RetryPolicy().retryOn(Exception.class);
    assertTrue(policy.allowsRetriesFor(null, new Exception()));
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));

    policy = new RetryPolicy().retryOn(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertTrue(policy.allowsRetriesFor(null, new IOException()));
    assertFalse(policy.allowsRetriesFor(null, new RuntimeException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));

    policy = new RetryPolicy().retryOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertFalse(policy.allowsRetriesFor(null, new RuntimeException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testAllowsRetriesForResult() {
    RetryPolicy policy = new RetryPolicy().retryWhen(10);
    assertTrue(policy.allowsRetriesFor(10, null));
    assertFalse(policy.allowsRetriesFor(5, null));
  }

  public void testNotAllowsRetriesForAbortableCompletionPredicate() {
    RetryPolicy policy = new RetryPolicy()
        .abortIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertFalse(policy.allowsRetriesFor("test", null));
    // No retries needed for successful result
    assertFalse(policy.allowsRetriesFor(0, null));
    assertFalse(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertTrue(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testNotAllowsRetriesForAbortableFailurePredicate() {
    RetryPolicy policy = new RetryPolicy().abortOn(failure -> failure instanceof ConnectException)
        .retryOn(failure -> failure instanceof IllegalArgumentException);
    assertFalse(policy.allowsRetriesFor(null, new ConnectException()));
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testNotAllowsRetriesForAbortableResultPredicate() {
    RetryPolicy policy = new RetryPolicy().abortIf((Integer result) -> result > 100);
    assertFalse(policy.allowsRetriesFor(110, null));
    // No retries needed for successful result
    assertFalse(policy.allowsRetriesFor(50, null));
    assertTrue(policy.allowsRetriesFor(50, new IllegalArgumentException()));
  }

  @SuppressWarnings("unchecked")
  public void testDoesNotAllowRetriesForAbortableFailure() {
    RetryPolicy policy = new RetryPolicy().abortOn(Exception.class);
    assertFalse(policy.allowsRetriesFor(null, new Exception()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalArgumentException()));

    policy = new RetryPolicy().abortOn(IllegalArgumentException.class, IOException.class);
    assertFalse(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertFalse(policy.allowsRetriesFor(null, new IOException()));
    assertTrue(policy.allowsRetriesFor(null, new RuntimeException()));
    assertTrue(policy.allowsRetriesFor(null, new IllegalStateException()));

    policy = new RetryPolicy().abortOn(Arrays.asList(IllegalArgumentException.class));
    assertFalse(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertTrue(policy.allowsRetriesFor(null, new RuntimeException()));
    assertTrue(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testDoesNotAllowRetriesForAbortableResult() {
    RetryPolicy policy = new RetryPolicy().abortWhen(10).retryWhen(-1);
    assertFalse(policy.allowsRetriesFor(10, null));
    // No retries needed for successful result
    assertFalse(policy.allowsRetriesFor(5, null));
    assertTrue(policy.allowsRetriesFor(-1, null));
    assertTrue(policy.allowsRetriesFor(5, new IllegalArgumentException()));
  }

  public void shouldRequireValidBackoff() {
    shouldFail(() -> new RetryPolicy().withBackoff(0, 0, null), NullPointerException.class);
    shouldFail(
        () -> new RetryPolicy().withMaxDuration(1, TimeUnit.MILLISECONDS).withBackoff(100, 120, TimeUnit.MILLISECONDS),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(-3, 10, TimeUnit.MILLISECONDS), IllegalArgumentException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(100, 10, TimeUnit.MILLISECONDS), IllegalArgumentException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(5, 10, TimeUnit.MILLISECONDS, .5), IllegalArgumentException.class);
  }

  public void shouldRequireValidInterval() {
    shouldFail(() -> new RetryPolicy().withDelay(5, null), NullPointerException.class);
    shouldFail(() -> new RetryPolicy().withMaxDuration(1, TimeUnit.MILLISECONDS).withDelay(100, TimeUnit.MILLISECONDS),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withBackoff(1, 2, TimeUnit.MILLISECONDS).withDelay(100, TimeUnit.MILLISECONDS),
        IllegalStateException.class);
    shouldFail(() -> new RetryPolicy().withDelay(-1, TimeUnit.MILLISECONDS), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxRetries() {
    shouldFail(() -> new RetryPolicy().withMaxRetries(-4), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxDuration() {
    shouldFail(
        () -> new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS).withMaxDuration(100, TimeUnit.MILLISECONDS),
        IllegalStateException.class);
  }

  public void testCopy() {
    RetryPolicy rp = new RetryPolicy();
    rp.withBackoff(2, 20, TimeUnit.SECONDS, 2.5);
    rp.withMaxDuration(60, TimeUnit.SECONDS);
    rp.withMaxRetries(3);

    RetryPolicy rp2 = rp.copy();
    assertEquals(rp.getDelay().toNanos(), rp2.getDelay().toNanos());
    assertEquals(rp.getDelayMultiplier(), rp2.getDelayMultiplier());
    assertEquals(rp.getMaxDelay().toNanos(), rp2.getMaxDelay().toNanos());
    assertEquals(rp.getMaxDuration().toNanos(), rp2.getMaxDuration().toNanos());
    assertEquals(rp.getMaxRetries(), rp2.getMaxRetries());
  }
}
