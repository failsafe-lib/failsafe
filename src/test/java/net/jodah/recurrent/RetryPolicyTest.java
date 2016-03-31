package net.jodah.recurrent;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
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
    assertFalse(policy.allowsRetriesFor(0, null));
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  @SuppressWarnings("unchecked")
  public void testAllowsRetriesForFailure() {
    RetryPolicy policy = new RetryPolicy().retryOn(IllegalArgumentException.class, IOException.class)
        .retryOn(failure -> failure instanceof ConnectException);

    // Check failure types
    assertTrue(policy.allowsRetriesFor(null, new IllegalArgumentException()));
    assertTrue(policy.allowsRetriesFor(null, new RuntimeException()));
    assertTrue(policy.allowsRetriesFor(null, new IOException()));
    assertTrue(policy.allowsRetriesFor(null, new Exception()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));

    // Check failure predicate
    assertTrue(policy.allowsRetriesFor(null, new ConnectException()));
    assertFalse(policy.allowsRetriesFor(null, new IllegalStateException()));
  }

  public void testAllowsRetriesForResult() {
    RetryPolicy policy = new RetryPolicy().retryWhen(10).retryIf((Integer result) -> result > 100);

    // Check result value
    assertTrue(policy.allowsRetriesFor(10, null));
    assertFalse(policy.allowsRetriesFor(5, null));

    // Check result predicate
    assertTrue(policy.allowsRetriesFor(110, null));
    assertFalse(policy.allowsRetriesFor(50, null));
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
}
