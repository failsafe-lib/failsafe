package net.jodah.failsafe.issues;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.RetryPolicy;

/**
 * Tests https://github.com/jhalterman/failsafe/issues/115 and https://github.com/jhalterman/failsafe/issues/116
 */
@Test
public class Issue115 {
  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldFailWithJitterLargerThanDelay() {
    new RetryPolicy().retryOn(IllegalArgumentException.class).withDelay(100, TimeUnit.MILLISECONDS).withJitter(200,
        TimeUnit.MILLISECONDS);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldFailWithJitterWithNoDelay() {
    new RetryPolicy().retryOn(IllegalArgumentException.class).withJitter(200, TimeUnit.MILLISECONDS);
  }
}
