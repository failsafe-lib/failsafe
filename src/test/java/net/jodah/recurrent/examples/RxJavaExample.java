package net.jodah.recurrent.examples;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.recurrent.RetryPolicy;
import net.jodah.recurrent.RetryStats;
import rx.Observable;
import rx.Subscriber;

@Test
public class RxJavaExample {
  public void example() throws Throwable {
    AtomicInteger failures = new AtomicInteger();
    RetryPolicy retryPolicy = new RetryPolicy().withDelay(1, TimeUnit.SECONDS);

    Observable.create((Subscriber<? super String> s) -> {
      // Fail 3 times then succeed
      if (failures.getAndIncrement() < 3)
        s.onError(new RuntimeException());
      else
        System.out.println("Subscriber completed successfully");
    }).retryWhen(attempts -> {
      RetryStats stats = new RetryStats(retryPolicy);
      return attempts.flatMap(failure -> {
        System.out.println("Failure detected");
        if (stats.canRetryOn(failure))
          return Observable.timer(stats.getWaitTime(), TimeUnit.NANOSECONDS);
        else
          return Observable.error(failure);
      });
    }).toBlocking().forEach(System.out::println);
  }
}
