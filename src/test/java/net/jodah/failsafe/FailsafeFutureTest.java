package net.jodah.failsafe;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.Test;

@Test
public class FailsafeFutureTest {
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

  @Test(expectedExceptions = TimeoutException.class)
  public void shouldGetWithTimeout() throws Throwable {
    Failsafe.with(new RetryPolicy()).with(executor).run(() -> {
      Thread.sleep(1000);
    }).get(100, TimeUnit.MILLISECONDS);

    Thread.sleep(1000);
  }
}
