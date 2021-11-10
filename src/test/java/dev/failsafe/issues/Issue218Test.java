package dev.failsafe.issues;

import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

@Test
public class Issue218Test {
  public void test() {
    RetryPolicy<Void> retryPolicy = RetryPolicy.<Void>builder().withMaxAttempts(2).build();
    Fallback<Void> fallback = Fallback.none();
    Failsafe.with(fallback, retryPolicy).run(() -> {
      throw new Exception();
    });
  }
}
