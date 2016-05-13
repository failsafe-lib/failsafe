package net.jodah.failsafe.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

public class AsyncExample {
  static ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  static RetryPolicy retryPolicy = new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS);
  static Service service = new Service();

  public static class Service {
    AtomicInteger failures = new AtomicInteger();

    // Fail 3 times then succeed
    CompletableFuture<Boolean> connect() {
      CompletableFuture<Boolean> future = new CompletableFuture<>();
      executor.submit(() -> {
        if (failures.getAndIncrement() < 3)
          future.completeExceptionally(new RuntimeException());
        else
          future.complete(true);
      });
      return future;
    }
  }

  public static void main(String... args) throws Throwable {
    Failsafe.with(retryPolicy)
        .with(executor)
        .getAsync(execution -> service.connect().whenComplete((result, failure) -> {
          if (execution.complete(result, failure))
            System.out.println("Success");
          else if (!execution.retry())
            System.out.println("Connection attempts failed");
        }));

    Thread.sleep(3000);
  }
}
