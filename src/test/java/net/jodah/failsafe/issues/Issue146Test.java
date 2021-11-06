package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Tests https://github.com/jhalterman/failsafe/issues/146
 */
@Test
public class Issue146Test {
  public void shouldRespectFailureCondition() {
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger failureCounter = new AtomicInteger();
    AtomicInteger failedAttemptCounter = new AtomicInteger();
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
      .handleResultIf(Objects::isNull)
      .withMaxRetries(2)
      .build()
      .onSuccess(e -> successCounter.incrementAndGet())
      .onFailure(e -> failureCounter.incrementAndGet())
      .onFailedAttempt(e -> failedAttemptCounter.incrementAndGet());

    Failsafe.with(retryPolicy).get(() -> null);

    assertEquals(3, failedAttemptCounter.get());
    assertEquals(0, successCounter.get());
    assertEquals(1, failureCounter.get());
  }
}
