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
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Tests failsafe with an executor.
 */
@Test
public class ExecutorTest extends Testing {
  AtomicInteger executions = new AtomicInteger();
  Executor executor = runnable -> {
    executions.incrementAndGet();
    runnable.run();
  };

  @BeforeMethod
  protected void beforeMethod() {
    executions.set(0);
  }

  public void testExecutorWithSyncExecution() {
    assertThrows(() -> Failsafe.with(RetryPolicy.ofDefaults()).with(executor).run(() -> {
      throw new IllegalStateException();
    }), IllegalStateException.class);
    assertEquals(executions.get(), 3);
  }

  public void testExecutorWithAsyncExecution() {
    assertThrows(() -> Failsafe.with(RetryPolicy.ofDefaults()).with(executor).runAsync(() -> {
      throw new IllegalStateException();
    }).get(), ExecutionException.class, IllegalStateException.class);
    assertEquals(executions.get(), 3);
  }
}
