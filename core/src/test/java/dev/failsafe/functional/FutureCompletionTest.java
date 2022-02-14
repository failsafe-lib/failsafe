/*
 * Copyright 2018 the original author or authors.
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
package dev.failsafe.functional;

import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import dev.failsafe.Failsafe;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertFalse;

/**
 * Tests behavior when a FailsafeFuture is explicitly completed.
 */
@Test
public class FutureCompletionTest extends Testing {
  /**
   * Asserts that an externally completed FailsafeFuture works as expected.
   */
  public void shouldCompleteFutureExternally() throws Throwable {
    // Given
    Waiter waiter = new Waiter();
    CompletableFuture<Boolean> future1 = Failsafe.with(retryNever).onSuccess(e -> {
      waiter.assertFalse(e.getResult());
      waiter.resume();
    }).getAsync(() -> {
      waiter.resume();
      Thread.sleep(500);
      return true;
    });
    waiter.await(1000);

    // When completed
    future1.complete(false);

    // Then
    assertFalse(future1.get());
    waiter.await(1000);

    // Given
    CompletableFuture<Boolean> future2 = Failsafe.with(retryNever).onFailure(e -> {
      waiter.assertTrue(e.getException() instanceof IllegalArgumentException);
      waiter.resume();
    }).getAsync(() -> {
      waiter.resume();
      Thread.sleep(500);
      return true;
    });
    waiter.await(1000);

    // When completed exceptionally
    future2.completeExceptionally(new IllegalArgumentException());

    // Then
    assertThrows(() -> unwrapExceptions(future2::get), IllegalArgumentException.class);
    waiter.await(1000);
  }
}
