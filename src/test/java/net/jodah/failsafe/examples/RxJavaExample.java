package net.jodah.failsafe.examples;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.jodah.failsafe.Execution;
import net.jodah.failsafe.RetryPolicy;
import rx.Observable;
import rx.Subscriber;

public class RxJavaExample {
  public static void main(String... args) throws Throwable {
    AtomicInteger failures = new AtomicInteger();
    RetryPolicy retryPolicy = new RetryPolicy().withDelay(1, TimeUnit.SECONDS);

    Observable.create((Subscriber<? super String> s) -> {
      // Fail 3 times then succeed
      if (failures.getAndIncrement() < 3)
        s.onError(new RuntimeException());
      else
        System.out.println("Subscriber completed successfully");
    }).retryWhen(attempts -> {
      Execution context = new Execution(retryPolicy);
      return attempts.flatMap(failure -> {
        System.out.println("Failure detected");
        if (context.canRetryOn(failure))
          return Observable.timer(context.getWaitTime().toNanos(), TimeUnit.NANOSECONDS);
        else
          return Observable.error(failure);
      });
    }).toBlocking().forEach(System.out::println);
  }
}
