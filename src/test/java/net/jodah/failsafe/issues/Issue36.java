package net.jodah.failsafe.issues;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

/**
 * https://github.com/jhalterman/failsafe/issues/36
 */
@Test
@SuppressWarnings("unchecked")
public class Issue36 {
  RetryPolicy retryPolicy = new RetryPolicy().retryIf((Boolean r) -> r == null || !r)
      .retryOn(Exception.class)
      .withMaxRetries(3);
  AtomicInteger calls;
  AtomicInteger failedAttempts;
  AtomicInteger retries;

  @BeforeMethod
  protected void beforeMethod() {
    calls = new AtomicInteger();
    failedAttempts = new AtomicInteger();
    retries = new AtomicInteger();
  }

  public void test() {
    try {
      Failsafe.with(retryPolicy)
          .onFailedAttempt((r, f, c) -> failedAttempts.incrementAndGet())
          .onRetry(e -> retries.incrementAndGet())
          .get(() -> {
            calls.incrementAndGet();
            throw new Exception();
          });
      fail();
    } catch (Exception expected) {
    }

    // Then
    assertCounters();
  }

  void assertCounters() {
    assertEquals(calls.get(), 4);
    assertEquals(failedAttempts.get(), 4);
    assertEquals(retries.get(), 3);
  }

  @Test
  public void failedAttemptListener_WithFailedResponses_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().retryIf((Boolean response) -> response != null && !response)
        .retryOn(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    Failsafe.<Boolean>with(policy)
        .onFailedAttempt((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
        .get(() -> false);
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithFailedResponses_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().retryIf((Boolean response) -> response != null && !response)
        .retryOn(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    Failsafe.<Boolean>with(policy)
        .onRetry((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
        .get(() -> false);
    assertEquals(listenerCallbacks.get(), 3);
  }

  @Test
  public void failedAttemptListener_WithExceptions_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().retryIf((Boolean response) -> response != null && !response)
        .retryOn(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    try {
      Failsafe.<Boolean>with(policy)
          .onFailedAttempt((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
          .get(() -> {
            throw new RuntimeException();
          });
    } catch (RuntimeException e) {
    }
    assertEquals(listenerCallbacks.get(), 4);
  }

  @Test
  public void retryListener_WithExceptions_ShouldBeCalled() throws Exception {
    RetryPolicy policy = new RetryPolicy().retryIf((Boolean response) -> response != null && !response)
        .retryOn(Exception.class)
        .withMaxRetries(3);
    AtomicInteger listenerCallbacks = new AtomicInteger();
    try {
      Failsafe.<Boolean>with(policy)
          .onRetry((failedResponse, exception, context) -> listenerCallbacks.incrementAndGet())
          .get(() -> {
            throw new RuntimeException();
          });
    } catch (RuntimeException e) {
    }
    assertEquals(listenerCallbacks.get(), 3);
  }
}
