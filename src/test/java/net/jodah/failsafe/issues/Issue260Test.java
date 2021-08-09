package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Timeout;
import net.jodah.failsafe.function.ContextualRunnable;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

@Test
public class Issue260Test {
  public void test() throws Throwable {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(300))
      .onFailure(e -> System.out.println("Interrupted"))
      .withInterrupt(true);
    RetryPolicy<Object> rp = new RetryPolicy<>().onRetry(e -> System.out.println("Retrying"))
      .onSuccess(e -> System.out.println("Success"));

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
    f1.get();
    f2.get();
    f3.get();
  }
}
