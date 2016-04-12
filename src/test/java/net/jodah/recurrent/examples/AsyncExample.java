package net.jodah.recurrent.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RetryPolicy;

@Test
public class AsyncExample {
  private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  RetryPolicy retryPolicy = new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS);
  private Service service = new Service();

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

  public void example() throws Throwable {
    Recurrent.with(retryPolicy, executor)
        .getAsync(execution -> service.connect().whenComplete((result, failure) -> {
          if (execution.complete(result, failure))
            System.out.println("Success");
          else if (!execution.retry())
            System.out.println("Connection attempts failed");
        }));

    Thread.sleep(3000);
  }
}
