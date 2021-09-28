package net.jodah.failsafe.testing;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.FailurePolicy;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Timeout;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logging and stats for tests.
 */
public class Logging extends Mocking {
  public static class Stats {
    // Common
    public volatile int executionCount;
    public volatile int failureCount;
    public volatile int successCount;

    // RetryPolicy
    public volatile int failedAttemptCount;
    public volatile int retryCount;
    public volatile int retryScheduledCount;
    public volatile int retriesExceededCount;
    public volatile int abortCount;

    // CircuitBreaker
    public volatile int openCount;
    public volatile int halfOpenCount;
    public volatile int closedCount;

    public void reset() {
      executionCount = 0;
      failureCount = 0;
      successCount = 0;
      failedAttemptCount = 0;
      retryCount = 0;
      retryScheduledCount = 0;
      retriesExceededCount = 0;
      abortCount = 0;
      openCount = 0;
      halfOpenCount = 0;
      closedCount = 0;
    }
  }

  static volatile long lastTimestamp;

  public static void log(Object object, String msg, Object... args) {
    Class<?> clazz = object instanceof Class ? (Class<?>) object : object.getClass();
    log(clazz.getSimpleName() + " " + String.format(msg, args));
  }

  public static void log(Class<?> clazz, String msg) {
    log(clazz.getSimpleName() + " " + msg);
  }

  public static void log(String msg) {
    long currentTimestamp = System.currentTimeMillis();
    if (lastTimestamp + 80 < currentTimestamp)
      System.out.printf("%n%n");
    lastTimestamp = currentTimestamp;
    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss.SSS"));
    StringBuilder threadName = new StringBuilder(Thread.currentThread().getName());
    for (int i = threadName.length(); i < 35; i++)
      threadName.append(" ");
    System.out.println("[" + time + "] " + "[" + threadName + "] " + msg);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T> RetryPolicy<T> withLogs(RetryPolicy<T> retryPolicy) {
    return withStatsAndLogs(retryPolicy, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T> Timeout<T> withLogs(Timeout<T> timeout) {
    return withStatsAndLogs(timeout, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T> CircuitBreaker<T> withLogs(CircuitBreaker<T> circuitBreaker) {
    return withStatsAndLogs(circuitBreaker, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T extends FailurePolicy<T, R>, R> T withLogs(T policy) {
    return withStatsAndLogs(policy, new Stats(), true);
  }

  public static <T> RetryPolicy<T> withStats(RetryPolicy<T> retryPolicy, Stats stats) {
    return withStatsAndLogs(retryPolicy, stats, false);
  }

  public static <T> RetryPolicy<T> withStatsAndLogs(RetryPolicy<T> retryPolicy, Stats stats) {
    return withStatsAndLogs(retryPolicy, stats, true);
  }

  private static <T> RetryPolicy<T> withStatsAndLogs(RetryPolicy<T> retryPolicy, Stats stats, boolean withLogging) {
    retryPolicy.onFailedAttempt(e -> {
      stats.executionCount++;
      stats.failedAttemptCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s failed attempt [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          retryPolicy.hashCode(), e.getLastResult(), e.getLastFailure(), e.getAttemptCount(), e.getExecutionCount());
    }).onRetry(e -> {
      stats.retryCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retrying [result: %s, failure: %s]%n", retryPolicy.hashCode(),
          e.getLastResult(), e.getLastFailure());
    }).onRetryScheduled(e -> {
      stats.retryScheduledCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s scheduled [delay: %s ms]%n", retryPolicy.hashCode(), e.getDelay().toMillis());
    }).onRetriesExceeded(e -> {
      stats.retriesExceededCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retries exceeded%n", retryPolicy.hashCode());
    }).onAbort(e -> {
      stats.abortCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s abort%n", retryPolicy.hashCode());
    });
    withStatsAndLogs((FailurePolicy) retryPolicy, stats, withLogging);
    return retryPolicy;
  }

  public static <T> Timeout<T> withStats(Timeout<T> timeout, Stats stats) {
    return withStatsAndLogs(timeout, stats, false);
  }

  public static <T> Timeout<T> withStatsAndLogs(Timeout<T> timeout, Stats stats) {
    return withStatsAndLogs(timeout, stats, true);
  }

  private static <T> Timeout<T> withStatsAndLogs(Timeout<T> timeout, Stats stats, boolean withLogging) {
    return timeout.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("Timeout %s success policy executions=%s, successes=%s%n", timeout.hashCode(),
          stats.executionCount, stats.successCount);
    }).onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("Timeout %s exceeded policy executions=%s, failure=%s%n", timeout.hashCode(),
          stats.executionCount, stats.failureCount);
    });
  }

  public static <T> CircuitBreaker<T> withStats(CircuitBreaker<T> circuitBreaker, Stats stats) {
    return withStatsAndLogs(circuitBreaker, stats, false);
  }

  public static <T> CircuitBreaker<T> withStatsAndLogs(CircuitBreaker<T> circuitBreaker, Stats stats) {
    return withStatsAndLogs(circuitBreaker, stats, true);
  }

  private static <T> CircuitBreaker<T> withStatsAndLogs(CircuitBreaker<T> circuitBreaker, Stats stats,
    boolean withLogging) {
    circuitBreaker.onOpen(() -> {
      stats.openCount++;
      if (withLogging)
        System.out.println("CircuitBreaker opening");
    }).onHalfOpen(() -> {
      stats.halfOpenCount++;
      if (withLogging)
        System.out.println("CircuitBreaker half-opening");
    }).onClose(() -> {
      stats.closedCount++;
      if (withLogging)
        System.out.println("CircuitBreaker closing");
    });
    withStatsAndLogs((FailurePolicy) circuitBreaker, stats, withLogging);
    return circuitBreaker;
  }

  public static <T extends FailurePolicy<T, R>, R> T withStats(T policy, Stats stats) {
    return withStatsAndLogs(policy, stats, false);
  }

  public static <T extends FailurePolicy<T, R>, R> T withStatsAndLogs(T policy, Stats stats) {
    return withStatsAndLogs(policy, stats, true);
  }

  private static <T extends FailurePolicy<T, R>, R> T withStatsAndLogs(T policy, Stats stats, boolean withLogging) {
    return policy.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("%s success [result: %s, attempts: %s, executions: %s]%n", policy.getClass().getSimpleName(),
          e.getResult(), e.getAttemptCount(), e.getExecutionCount());
    }).onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("%s failure [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          policy.getClass().getSimpleName(), e.getResult(), e.getFailure(), e.getAttemptCount(), e.getExecutionCount());
    });
  }
}
