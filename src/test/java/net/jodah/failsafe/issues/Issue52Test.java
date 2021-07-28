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

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class Issue52Test {
  ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

  @AfterClass
  protected void afterClass() {
    scheduler.shutdownNow();
  }

  @Test(expectedExceptions = CancellationException.class)
  public void shouldCancelExecutionViaFuture() throws Throwable {
    Future<Object> proxyFuture = Failsafe.with(new RetryPolicy<>().withDelay(Duration.ofMillis(10)))
        .with(scheduler)
        .getAsync(exec -> {
          throw new IllegalStateException();
        });

    assertTrue(proxyFuture.cancel(true));
    proxyFuture.get(); // should throw CancellationException per .getAsync() javadoc.
  }

  public void shouldCancelExecutionViaCompletableFuture() throws Throwable {
    AtomicInteger counter = new AtomicInteger();
    CompletableFuture<String> proxyFuture = Failsafe.with(new RetryPolicy<>().withDelay(Duration.ofMillis(10)))
        .with(scheduler)
        .getStageAsync(exec -> {
          Thread.sleep(100);
          counter.incrementAndGet();
          CompletableFuture<String> result = new CompletableFuture<>();
          result.completeExceptionally(new RuntimeException());
          return result;
        });

    assertTrue(proxyFuture.cancel(true));
    int count = counter.get();

    assertTrue(proxyFuture.isCancelled());
    Asserts.assertThrows(proxyFuture::get, CancellationException.class);

    // Assert that execution has actually stopped
    Thread.sleep(20);
    assertEquals(count, counter.get());
  }
}
