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

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class Issue9Test {
  public interface Service {
    boolean connect();
  }

  public void test() throws Throwable {
    // Given - Fail twice then succeed
    AtomicInteger retryCounter = new AtomicInteger();
    Service service = mock(Service.class);
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(true);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    Waiter waiter = new Waiter();

    // When
    AtomicInteger successCounter = new AtomicInteger();
    Future<Boolean> future = Failsafe.with(new RetryPolicy().withMaxRetries(2))
        .with(executor)
        .onRetry((r, p) -> retryCounter.incrementAndGet())
        .onSuccess(p -> {
          successCounter.incrementAndGet();
          waiter.resume();
        })
        .getAsync(service::connect);

    // Then
    waiter.await(1000);
    verify(service, times(3)).connect();
    assertTrue(future.get());
    assertEquals(retryCounter.get(), 2);
    assertEquals(successCounter.get(), 1);
  }
}
