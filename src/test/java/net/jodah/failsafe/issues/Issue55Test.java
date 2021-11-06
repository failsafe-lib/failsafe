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
package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test
public class Issue55Test {
  public void shouldOnlyFallbackOnFailure() throws Throwable {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    AtomicInteger counter = new AtomicInteger();
    Failsafe.with(Fallback.of(counter::incrementAndGet), RetryPolicy.ofDefaults()).with(executor).getAsync(() -> null);

    Thread.sleep(100);
    assertEquals(counter.get(), 0);

    Failsafe.with(Fallback.of(counter::incrementAndGet), RetryPolicy.builder().withMaxRetries(1).build())
        .with(executor)
        .runAsync(() -> {
          throw new RuntimeException();
        });

    Thread.sleep(100);
    assertEquals(counter.get(), 1);
    executor.shutdownNow();
  }
}
