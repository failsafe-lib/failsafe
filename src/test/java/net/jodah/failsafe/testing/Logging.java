package net.jodah.failsafe.testing;

import net.jodah.failsafe.*;

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
  public static <T> RetryPolicyBuilder<T> withLogs(RetryPolicyBuilder<T> retryPolicy) {
    return withStatsAndLogs(retryPolicy, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T> TimeoutBuilder<T> withLogs(TimeoutBuilder<T> builder) {
    return withStatsAndLogs(builder, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T> CircuitBreakerBuilder<T> withLogs(CircuitBreakerBuilder<T> builder) {
    return withStatsAndLogs(builder, new Stats(), true);
  }

  /**
   * Note: The internal stats that are logged are not reset, even across multiple executions.
   */
  public static <T extends PolicyBuilder<?, ? extends PolicyConfig<R>, R>, R> T withLogs(T policy) {
    return withStatsAndLogs(policy, new Stats(), true);
  }

  public static <T> RetryPolicyBuilder<T> withStats(RetryPolicyBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, false);
  }

  public static <T> RetryPolicyBuilder<T> withStatsAndLogs(RetryPolicyBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, true);
  }

  private static <T> RetryPolicyBuilder<T> withStatsAndLogs(RetryPolicyBuilder<T> builder, Stats stats,
    boolean withLogging) {
    builder.onFailedAttempt(e -> {
      stats.executionCount++;
      stats.failedAttemptCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s failed attempt [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          builder.hashCode(), e.getLastResult(), e.getLastFailure(), e.getAttemptCount(), e.getExecutionCount());
    }).onRetry(e -> {
      stats.retryCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retrying [result: %s, failure: %s]%n", builder.hashCode(), e.getLastResult(),
          e.getLastFailure());
    }).onRetryScheduled(e -> {
      stats.retryScheduledCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s scheduled [delay: %s ms]%n", builder.hashCode(), e.getDelay().toMillis());
    }).onRetriesExceeded(e -> {
      stats.retriesExceededCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retries exceeded%n", builder.hashCode());
    }).onAbort(e -> {
      stats.abortCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s abort%n", builder.hashCode());
    });
    withStatsAndLogs((PolicyBuilder) builder, stats, withLogging);
    return builder;
  }

  public static <T> TimeoutBuilder<T> withStats(TimeoutBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, false);
  }

  public static <T> TimeoutBuilder<T> withStatsAndLogs(TimeoutBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, true);
  }

  private static <T> TimeoutBuilder<T> withStatsAndLogs(TimeoutBuilder<T> builder, Stats stats, boolean withLogging) {
    return builder.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("Timeout %s success policy executions=%s, successes=%s%n", builder.hashCode(),
          stats.executionCount, stats.successCount);
    }).onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("Timeout %s exceeded policy executions=%s, failure=%s%n", builder.hashCode(),
          stats.executionCount, stats.failureCount);
    });
  }

  public static <T> CircuitBreakerBuilder<T> withStats(CircuitBreakerBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, false);
  }

  public static <T> CircuitBreakerBuilder<T> withStatsAndLogs(CircuitBreakerBuilder<T> builder, Stats stats) {
    return withStatsAndLogs(builder, stats, true);
  }

  private static <T> CircuitBreakerBuilder<T> withStatsAndLogs(CircuitBreakerBuilder<T> builder, Stats stats,
    boolean withLogging) {
    builder.onOpen(e -> {
      stats.openCount++;
      if (withLogging)
        System.out.println("CircuitBreaker opening");
    }).onHalfOpen(e -> {
      stats.halfOpenCount++;
      if (withLogging)
        System.out.println("CircuitBreaker half-opening");
    }).onClose(e -> {
      stats.closedCount++;
      if (withLogging)
        System.out.println("CircuitBreaker closing");
    });
    withStatsAndLogs((PolicyBuilder) builder, stats, withLogging);
    return builder;
  }

  public static <T extends PolicyBuilder<?, ? extends PolicyConfig<R>, R>, R> T withStats(T builder, Stats stats) {
    return withStatsAndLogs(builder, stats, false);
  }

  public static <T extends PolicyBuilder<?, ? extends PolicyConfig<R>, R>, R> T withStatsAndLogs(T builder,
    Stats stats) {
    return withStatsAndLogs(builder, stats, true);
  }

  private static <T extends PolicyBuilder<?, ? extends PolicyConfig<R>, R>, R> T withStatsAndLogs(T builder,
    Stats stats, boolean withLogging) {
    builder.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("%s success [result: %s, attempts: %s, executions: %s]%n", builder.getClass().getSimpleName(),
          e.getResult(), e.getAttemptCount(), e.getExecutionCount());
    });
    builder.onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("%s failure [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          builder.getClass().getSimpleName(), e.getResult(), e.getFailure(), e.getAttemptCount(),
          e.getExecutionCount());
    });
    return builder;
  }
}
