package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.*;

@Test
public class Issue284Test {
  AtomicBoolean success;
  AtomicBoolean failure;
  AtomicBoolean executed;
  Fallback<String> fallback;
  RetryPolicy<String> retryPolicy = new RetryPolicy<String>().handleResult(null)
    .onSuccess(e -> success.set(true))
    .onFailure(e -> failure.set(true));

  @BeforeMethod
  protected void beforeMethod() {
    success = new AtomicBoolean();
    failure = new AtomicBoolean();
    executed = new AtomicBoolean();
  }

  private Fallback<String> fallbackFor(String result) {
    return Fallback.of(result).handleResult(null).onSuccess(e -> success.set(true)).onFailure(e -> failure.set(true));
  }

  public void testFallbackSuccess() {
    fallback = fallbackFor("hello");
    String result = Failsafe.with(fallback).get(() -> null);

    assertEquals(result, "hello");
    assertTrue(success.get(), "Fallback should have been successful");
  }

  public void testFallbackFailure() {
    fallback = fallbackFor(null);
    String result = Failsafe.with(fallback).get(() -> null);

    assertNull(result);
    assertTrue(failure.get(), "Fallback should have failed");
  }

  public void testRetryPolicySuccess() {
    String result = Failsafe.with(retryPolicy).get(() -> !executed.getAndSet(true) ? null : "hello");

    assertEquals(result, "hello");
    assertTrue(success.get(), "RetryPolicy should have been successful");
  }

  public void testRetryPolicyFailure() {
    String result = Failsafe.with(retryPolicy).get(() -> null);

    assertNull(result);
    assertTrue(failure.get(), "RetryPolicy should have failed");
  }
}
