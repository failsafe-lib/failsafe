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

import static org.testng.Assert.assertNull;
import static org.testng.Assert.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.RetryPolicy;

@Test
public class Issue52 {
  public void shouldCancelExecutionViaFuture() throws Throwable {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    FailsafeFuture<String> proxyFuture = Failsafe.with(new RetryPolicy().withDelay(10, TimeUnit.MILLISECONDS))
        .with(scheduler)
        .get(exec -> {
          throw new IllegalStateException();
        });

    Thread.sleep(100);
    proxyFuture.cancel(true);

    assertNull(proxyFuture.get());
  }

  public void shouldCancelExecutionViaCompletableFuture() throws Throwable {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    AtomicInteger counter = new AtomicInteger();
    CompletableFuture<String> proxyFuture = Failsafe.with(new RetryPolicy().withDelay(10, TimeUnit.MILLISECONDS))
        .with(scheduler)
        .future(exec -> {
          counter.incrementAndGet();
          CompletableFuture<String> result = new CompletableFuture<>();
          result.completeExceptionally(new RuntimeException());
          return result;
        });

    Thread.sleep(100);
    proxyFuture.cancel(true);
    int count = counter.get();

    assertTrue(proxyFuture.isCancelled());
    Asserts.assertThrows(() -> proxyFuture.get(), CancellationException.class);

    // Assert that execution has actually stopped
    Thread.sleep(20);
    assertEquals(count, counter.get());
  }
}
