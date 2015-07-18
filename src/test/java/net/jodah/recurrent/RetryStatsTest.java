package net.jodah.recurrent;

import static org.testng.Assert.*;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

/**
 * @author Jonathan Halterman
 */
@Test
public class RetryStatsTest {
  public void shouldSupportMaxRetries() throws Exception {
    RetryStats stats = new RetryStats(new RetryPolicy().withMaxRetries(3));
    assertTrue(stats.canRetry());
    assertTrue(stats.canRetry());
    assertTrue(stats.canRetry());
    assertFalse(stats.canRetry());
  }

  public void shouldSupportMaxDuration() throws Exception {
    RetryStats stats = new RetryStats(new RetryPolicy().withMaxDuration(100, TimeUnit.MILLISECONDS));
    assertTrue(stats.canRetry());
    assertTrue(stats.canRetry());
    Thread.sleep(100);
    assertFalse(stats.canRetry());
  }

  public void shouldTrackRetryCould() {
    RetryStats stats = new RetryStats(new RetryPolicy());
    stats.canRetry();
    stats.canRetry();
    assertEquals(stats.getRetryCount(), 2);
  }

  public void shouldAdjustWaitTimeForBackoff() {
    RetryStats stats = new RetryStats(new RetryPolicy().withBackoff(1, 10, TimeUnit.NANOSECONDS));
    assertEquals(stats.getWaitTime(), 1);
    stats.canRetry();
    assertEquals(stats.getWaitTime(), 2);
    stats.canRetry();
    assertEquals(stats.getWaitTime(), 4);
    stats.canRetry();
    assertEquals(stats.getWaitTime(), 8);
    stats.canRetry();
    assertEquals(stats.getWaitTime(), 10);
    stats.canRetry();
    assertEquals(stats.getWaitTime(), 10);
  }

  public void shouldAdjustWaitTimeForMaxDuration() throws Throwable {
    RetryStats stats = new RetryStats(
        new RetryPolicy().withDelay(49, TimeUnit.MILLISECONDS).withMaxDuration(50, TimeUnit.MILLISECONDS));
    Thread.sleep(10);
    assertTrue(stats.canRetry());
    assertTrue(stats.getWaitTime() < TimeUnit.MILLISECONDS.toNanos(50) && stats.getWaitTime() > 0);
  }
}
