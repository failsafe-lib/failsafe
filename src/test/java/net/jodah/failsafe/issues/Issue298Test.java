package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class Issue298Test {
  AtomicBoolean failedAttemptCalled = new AtomicBoolean();
  AtomicBoolean failureCalled = new AtomicBoolean();

  Fallback<String> fallback = Fallback.<String>builder(e -> "success")
    .onFailedAttempt(e -> failedAttemptCalled.set(true))
    .onFailure(e -> failureCalled.set(true))
    .build();

  @BeforeMethod
  protected void beforeMethod() {
    failedAttemptCalled.set(false);
    failureCalled.set(false);
  }

  public void testSync() {
    Failsafe.with(fallback).get(() -> {
      throw new Exception();
    });

    assertTrue(failedAttemptCalled.get());
    assertFalse(failureCalled.get());
  }

  public void testAsync() throws Throwable {
    Failsafe.with(fallback).getAsync(() -> {
      throw new Exception();
    }).get();

    assertTrue(failedAttemptCalled.get());
    assertFalse(failureCalled.get());
  }
}
