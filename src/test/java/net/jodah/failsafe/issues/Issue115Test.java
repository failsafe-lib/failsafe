package net.jodah.failsafe.issues;

import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Tests https://github.com/jhalterman/failsafe/issues/115 and https://github.com/jhalterman/failsafe/issues/116
 */
@Test
public class Issue115Test {
  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldFailWithJitterLargerThanDelay() {
    RetryPolicy.builder()
      .handle(IllegalArgumentException.class)
      .withDelay(Duration.ofMillis(100))
      .withJitter(Duration.ofMillis(200))
      .build();
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldFailWithJitterWithNoDelay() {
    RetryPolicy.builder().handle(IllegalArgumentException.class).withJitter(Duration.ofMillis(200)).build();
  }
}
