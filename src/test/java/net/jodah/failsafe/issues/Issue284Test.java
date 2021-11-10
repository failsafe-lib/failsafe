package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

@Test
public class Issue284Test {
  AtomicInteger failedAttempt;
  AtomicBoolean success;
  AtomicBoolean failure;
  AtomicBoolean executed;
  Fallback<String> fallback;
  RetryPolicy<String> retryPolicy = RetryPolicy.<String>builder()
    .handleResult(null)
    .onFailedAttempt(e -> failedAttempt.incrementAndGet())
    .onSuccess(e -> success.set(true))
    .onFailure(e -> failure.set(true))
    .build();

  @BeforeMethod
  protected void beforeMethod() {
    failedAttempt = new AtomicInteger();
    success = new AtomicBoolean();
    failure = new AtomicBoolean();
    executed = new AtomicBoolean();
  }

  private Fallback<String> fallbackFor(String result) {
    return Fallback.builder(result)
      .handleResult(null)
      .onFailedAttempt(e -> failedAttempt.incrementAndGet())
      .onSuccess(e -> success.set(true))
      .onFailure(e -> failure.set(true))
      .build();
  }

  public void testFallbackSuccess() {
    fallback = fallbackFor("hello");
    String result = Failsafe.with(fallback).get(() -> null);

    assertEquals(result, "hello");
    assertEquals(failedAttempt.get(), 1);
    assertTrue(success.get(), "Fallback should have been successful");
  }

  public void testFallbackFailure() {
    fallback = fallbackFor(null);
    String result = Failsafe.with(fallback).get(() -> null);

    assertNull(result);
    assertEquals(failedAttempt.get(), 1);
    assertTrue(failure.get(), "Fallback should have failed");
  }

  public void testRetryPolicySuccess() {
    String result = Failsafe.with(retryPolicy).get(() -> !executed.getAndSet(true) ? null : "hello");

    assertEquals(result, "hello");
    assertEquals(failedAttempt.get(), 1);
    assertTrue(success.get(), "RetryPolicy should have been successful");
  }

  public void testRetryPolicyFailure() {
    String result = Failsafe.with(retryPolicy).get(() -> null);

    assertNull(result);
    assertEquals(failedAttempt.get(), 3);
    assertTrue(failure.get(), "RetryPolicy should have failed");
  }
}
