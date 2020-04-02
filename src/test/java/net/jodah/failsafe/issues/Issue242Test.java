package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertTrue;

@Test
public class Issue242Test {
  public void test() throws Throwable {
    RetryPolicy<String> retryPolicy = new RetryPolicy<String>().handleResult(null)
      .withDelay(Duration.ofMillis(100))
      .withMaxAttempts(3);

    long startTime = System.currentTimeMillis();
    Failsafe.with(retryPolicy).runAsyncExecution(exec -> {
      if (!exec.complete(null, null))
        exec.retry();
    }).get();
    assertTrue(System.currentTimeMillis() - startTime > 200, "Expected delay between retries");
  }
}
