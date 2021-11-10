package dev.failsafe.issues;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import dev.failsafe.function.ContextualRunnable;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Test
public class Issue260Test {
  public void test() throws Throwable {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(300))
      .withInterrupt()
      .onFailure(e -> System.out.println("Interrupted"))
      .build();
    RetryPolicy<Object> rp = RetryPolicy.builder()
      .onRetry(e -> System.out.println("Retrying"))
      .onSuccess(e -> System.out.println("Success"))
      .build();

    Function<Integer, ContextualRunnable> task = (taskId) -> ctx -> {
      System.out.println("Starting execution of task " + taskId);
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        System.out.println("Interrupted task " + taskId);
        throw e;
      }
    };

    Future<?> f1 = Failsafe.with(rp, timeout).with(executor).runAsync(task.apply(1));
    Future<?> f2 = Failsafe.with(rp, timeout).with(executor).runAsync(task.apply(2));
    Future<?> f3 = Failsafe.with(rp, timeout).with(executor).runAsync(task.apply(3));
    f1.get(1, TimeUnit.SECONDS);
    f2.get(1, TimeUnit.SECONDS);
    f3.get(1, TimeUnit.SECONDS);
  }
}
