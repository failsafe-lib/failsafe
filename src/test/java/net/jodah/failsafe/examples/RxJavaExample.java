/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.examples;

import net.jodah.failsafe.Execution;
import net.jodah.failsafe.RetryPolicy;
import rx.Observable;
import rx.Subscriber;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RxJavaExample {
  public static void main(String... args) {
    AtomicInteger failures = new AtomicInteger();
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelay(Duration.ofSeconds(1));

    Observable.create((Subscriber<? super String> s) -> {
      // Fail 2 times then succeed
      if (failures.getAndIncrement() < 2)
        s.onError(new RuntimeException());
      else
        System.out.println("Subscriber completed successfully");
    }).retryWhen(attempts -> {
      Execution execution = new Execution(retryPolicy);
      return attempts.flatMap(failure -> {
        System.out.println("Failure detected");
        if (execution.canRetryOn(failure))
          return Observable.timer(execution.getWaitTime().toNanos(), TimeUnit.NANOSECONDS);
        else
          return Observable.<Long>error(failure);
      });
    }).toBlocking().forEach(System.out::println);
  }
}
