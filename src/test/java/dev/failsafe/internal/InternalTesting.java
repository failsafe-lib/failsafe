package dev.failsafe.internal;

import dev.failsafe.Bulkhead;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.RateLimiter;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public final class InternalTesting {
  private InternalTesting() {
  }

  @SuppressWarnings("unchecked")
  public static <T extends CircuitState<?>> T stateFor(CircuitBreaker<?> breaker) {
    Field stateField;
    try {
      stateField = CircuitBreakerImpl.class.getDeclaredField("state");
      stateField.setAccessible(true);
      return ((AtomicReference<T>) stateField.get(breaker)).get();
    } catch (Exception e) {
      throw new IllegalStateException("Could not get circuit breaker state");
    }
  }

  public static void resetBreaker(CircuitBreaker<?> breaker) {
    breaker.close();
    CircuitState<?> state = stateFor(breaker);
    state.getStats().reset();
  }

  public static void resetLimiter(RateLimiter<?> limiter) {
    try {
      RateLimiterImpl<?> impl = (RateLimiterImpl<?>) limiter;
      Field statsField = RateLimiterImpl.class.getDeclaredField("stats");
      statsField.setAccessible(true);
      RateLimiterStats stats = (RateLimiterStats) statsField.get(impl);
      stats.reset();
    } catch (Exception e) {
      throw new IllegalStateException("Could not reset rate limiter");
    }
  }

  public static void resetBulkhead(Bulkhead<?> bulkhead) {
    try {
      BulkheadImpl<?> impl = (BulkheadImpl<?>) bulkhead;
      Field semaphoreField = BulkheadImpl.class.getDeclaredField("semaphore");
      semaphoreField.setAccessible(true);
      Semaphore semaphore = (Semaphore) semaphoreField.get(impl);
      int totalPermits = impl.getConfig().getMaxConcurrency();
      semaphore.release(totalPermits - semaphore.availablePermits());
    } catch (Exception e) {
      throw new IllegalStateException("Could not reset rate limiter");
    }
  }
}
