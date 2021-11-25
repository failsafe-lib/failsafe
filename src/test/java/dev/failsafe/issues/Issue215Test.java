package dev.failsafe.issues;

import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

@Test
public class Issue215Test {
  public void test() {
    RetryPolicy.builder()
      .withBackoff(Duration.ofNanos(Long.MAX_VALUE).minusSeconds(1), Duration.ofSeconds(Long.MAX_VALUE), 1.50);
  }
}
