package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

@Test
public class Issue218Test {
  public void test() {
    RetryPolicy<Void> retryPolicy = new RetryPolicy<Void>().withMaxAttempts(2);
    Fallback<Void> fallback = Fallback.VOID;
    Failsafe.with(fallback, retryPolicy).run(() -> {
      throw new Exception();
    });
  }
}
