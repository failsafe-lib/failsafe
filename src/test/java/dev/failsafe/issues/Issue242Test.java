package dev.failsafe.issues;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

import static dev.failsafe.testing.Testing.withLogs;
import static org.testng.Assert.assertTrue;

@Test
public class Issue242Test {
  public void shouldDelayOnExplicitRetry() throws Throwable {
    RetryPolicy<String> retryPolicy = withLogs(
      RetryPolicy.<String>builder().handleResult(null).withDelay(Duration.ofMillis(110))).build();

    long startTime = System.currentTimeMillis();
    Failsafe.with(retryPolicy).runAsyncExecution(exec -> {
      exec.record(null, null);
    }).get();
    assertTrue(System.currentTimeMillis() - startTime > 200, "Expected delay between retries");
  }
}
