package net.jodah.failsafe;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.RetryPolicy;

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

  public void testCanRetryForNull() {
    RetryPolicy policy = new RetryPolicy();
    assertFalse(policy.canRetryFor(null, null));
  }

  public void testCanRetryForCompletionPredicate() {
    RetryPolicy policy = new RetryPolicy()
        .retryIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.canRetryFor("test", null));
    // No retries needed for successful result
    assertFalse(policy.canRetryFor(0, null));
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
    assertFalse(policy.canRetryFor(null, new IllegalStateException()));
  }

  public void testCanRetryForFailurePredicate() {
    RetryPolicy policy = new RetryPolicy().retryOn(failure -> failure instanceof ConnectException);
    assertTrue(policy.canRetryFor(null, new ConnectException()));
    assertFalse(policy.canRetryFor(null, new IllegalStateException()));
  }

  public void testCanRetryForResultPredicate() {
    RetryPolicy policy = new RetryPolicy().retryIf((Integer result) -> result > 100);
    assertTrue(policy.canRetryFor(110, null));
    assertFalse(policy.canRetryFor(50, null));
  }

  @SuppressWarnings("unchecked")
  public void testCanRetryForFailure() {
    RetryPolicy policy = new RetryPolicy();
    assertTrue(policy.canRetryFor(null, new Exception()));
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));

    policy = new RetryPolicy().retryOn(Exception.class);
    assertTrue(policy.canRetryFor(null, new Exception()));
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
    
    policy = new RetryPolicy().retryOn(RuntimeException.class);
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
    assertFalse(policy.canRetryFor(null, new Exception()));

    policy = new RetryPolicy().retryOn(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
    assertTrue(policy.canRetryFor(null, new IOException()));
    assertFalse(policy.canRetryFor(null, new RuntimeException()));
    assertFalse(policy.canRetryFor(null, new IllegalStateException()));

    policy = new RetryPolicy().retryOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.canRetryFor(null, new IllegalArgumentException()));
    assertFalse(policy.canRetryFor(null, new RuntimeException()));
    assertFalse(policy.canRetryFor(null, new IllegalStateException()));
  }

  public void testCanRetryForResult() {
    RetryPolicy policy = new RetryPolicy().retryWhen(10);
    assertTrue(policy.canRetryFor(10, null));
    assertFalse(policy.canRetryFor(5, null));
    assertTrue(policy.canRetryFor(5, new Exception()));
  }

  public void testCanAbortForNull() {
    RetryPolicy policy = new RetryPolicy();
    assertFalse(policy.canAbortFor(null, null));
  }

  public void testCanAbortForCompletionPredicate() {
    RetryPolicy policy = new RetryPolicy()
        .abortIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException);
    assertTrue(policy.canAbortFor("test", null));
    assertFalse(policy.canAbortFor(0, null));
    assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
    assertFalse(policy.canAbortFor(null, new IllegalStateException()));
  }

  public void testCanAbortForFailurePredicate() {
    RetryPolicy policy = new RetryPolicy().abortOn(failure -> failure instanceof ConnectException);
    assertTrue(policy.canAbortFor(null, new ConnectException()));
    assertFalse(policy.canAbortFor(null, new IllegalArgumentException()));
  }

  public void testCanAbortForResultPredicate() {
    RetryPolicy policy = new RetryPolicy().abortIf((Integer result) -> result > 100);
    assertTrue(policy.canAbortFor(110, null));
    assertFalse(policy.canAbortFor(50, null));
    assertFalse(policy.canAbortFor(50, new IllegalArgumentException()));
  }

  @SuppressWarnings("unchecked")
  public void testCanAbortForFailure() {
    RetryPolicy policy = new RetryPolicy().abortOn(Exception.class);
    assertTrue(policy.canAbortFor(null, new Exception()));
    assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));

    policy = new RetryPolicy().abortOn(IllegalArgumentException.class, IOException.class);
    assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
    assertTrue(policy.canAbortFor(null, new IOException()));
    assertFalse(policy.canAbortFor(null, new RuntimeException()));
    assertFalse(policy.canAbortFor(null, new IllegalStateException()));

    policy = new RetryPolicy().abortOn(Arrays.asList(IllegalArgumentException.class));
    assertTrue(policy.canAbortFor(null, new IllegalArgumentException()));
    assertFalse(policy.canAbortFor(null, new RuntimeException()));
    assertFalse(policy.canAbortFor(null, new IllegalStateException()));
  }

  public void testCanAbortForResult() {
    RetryPolicy policy = new RetryPolicy().abortWhen(10);
    assertTrue(policy.canAbortFor(10, null));
    assertFalse(policy.canAbortFor(5, null));
    assertFalse(policy.canAbortFor(5, new IllegalArgumentException()));
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

  public void shouldRequireValidDelay() {
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
    assertEquals(rp.getDelayFactor(), rp2.getDelayFactor());
    assertEquals(rp.getMaxDelay().toNanos(), rp2.getMaxDelay().toNanos());
    assertEquals(rp.getMaxDuration().toNanos(), rp2.getMaxDuration().toNanos());
    assertEquals(rp.getMaxRetries(), rp2.getMaxRetries());
  }
}
