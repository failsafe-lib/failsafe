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

import dev.failsafe.*;
import dev.failsafe.testing.Asserts;
import dev.failsafe.testing.Testing;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.*;

/**
 * Tests various execution interrupt scenarios.
 */
@Test
public class InterruptionTest extends Testing {
  /**
   * Asserts that Failsafe throws when interrupted while blocked in an execution.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousExecution() {
    Thread main = Thread.currentThread();
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(100);
        main.interrupt();
      } catch (InterruptedException e) {
      }
    });

    Asserts.assertThrows(() -> Failsafe.with(RetryPolicy.builder().withMaxRetries(0).build()).run(() -> {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
      fail("Expected interruption");
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that the thread's interrupt flag is set after interrupting a sync RetryPolicy delay.
   */
  public void shouldThrowWhenInterruptedDuringRetryPolicyDelay() {
    RetryPolicy<Object> rp = RetryPolicy.builder().withDelay(Duration.ofMillis(500)).build();
    Thread main = Thread.currentThread();
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(100);
        main.interrupt();
      } catch (InterruptedException e) {
      }
    });

    Asserts.assertThrows(() -> Failsafe.with(rp).run(() -> {
      throw new Exception();
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    Assert.assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that Failsafe throws when interrupting while blocked between executions.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousDelay() {
    Thread mainThread = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        mainThread.interrupt();
      } catch (Exception e) {
      }
    }).start();

    try {
      Failsafe.with(RetryPolicy.builder().withDelay(Duration.ofSeconds(5)).build()).run(() -> {
        throw new Exception();
      });
    } catch (Exception e) {
      assertTrue(e instanceof FailsafeException);
      assertTrue(e.getCause() instanceof InterruptedException);
      // Clear interrupt flag
      assertTrue(Thread.interrupted());
      return;
    }
    fail("Exception expected");
  }

  /**
   * Asserts that the interrrupt flag is reset when a sync execution is interrupted.
   */
  public void shouldResetInterruptFlag() {
    // Given
    Thread t = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        t.interrupt();
      } catch (InterruptedException e) {
      }
    }).start();

    // Then
    assertThrows(() -> Failsafe.with(retryNever).run(() -> {
      Thread.sleep(1000);
    }), FailsafeException.class, InterruptedException.class);
    t.interrupt();

    // Then
    assertTrue(Thread.interrupted());
  }

  /**
   * Ensures that an internally interrupted execution should always have the interrupt flag cleared afterwards.
   */
  public void shouldResetInterruptFlagAfterInterruption() throws Throwable {
    // Given
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(1)).withInterrupt().build();

    // When / Then
    testRunFailure(false, Failsafe.with(timeout), ctx -> {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, (f, e) -> {
      assertFalse(Thread.currentThread().isInterrupted(), "Interrupt flag should be cleared after Failsafe handling");
    }, TimeoutExceededException.class);
  }
}
