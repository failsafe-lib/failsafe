package net.jodah.failsafe.issues;

import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Test
public class Issue224Test {
  public void test() {
    RetryPolicy.builder().withDelay(10, 100, ChronoUnit.MILLIS).withJitter(Duration.ofMillis(5)).build();
    RetryPolicy.builder().withDelay(1, 100, ChronoUnit.MILLIS).withJitter(.5).build();
  }
}
