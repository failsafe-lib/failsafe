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
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the configuration of an Executor.
 */
@Test
public class ExecutorConfigurationTest extends Testing {
  AtomicBoolean executorCalled;
  AtomicBoolean executionCalled;
  RetryPolicy<String> retryPolicy = withLogs(RetryPolicy.<String>builder()).build();

  Executor executor = execution -> {
    executorCalled.set(true);
    execution.run();
  };

  Executor throwingExecutor = execution -> {
    executorCalled.set(true);
    execution.run();
    throw new IllegalStateException();
  };

  @BeforeMethod
  protected void beforeMethod() {
    executorCalled = new AtomicBoolean();
    executionCalled = new AtomicBoolean();
  }

  public void testSyncExecutionSuccess() {
    String result = Failsafe.with(retryPolicy).with(executor).get(() -> {
      executionCalled.set(true);
      return "result";
    });

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertNull(result);
  }

  public void testSyncExecutionFailure() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(executor).run(() -> {
      executionCalled.set(true);
      throw new IllegalStateException();
    }), IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }

  public void testSyncExecutionThatThrowsFromTheExecutor() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(throwingExecutor).run(() -> {
      executionCalled.set(true);
    }), IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }

  public void testAsyncExecutionSuccess() throws Throwable {
    AtomicBoolean fjpAssertion = new AtomicBoolean();

    String result = Failsafe.with(retryPolicy).with(executor).getAsync(() -> {
      fjpAssertion.set(Thread.currentThread() instanceof ForkJoinWorkerThread);
      executionCalled.set(true);
      return "result";
    }).get();

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertTrue(fjpAssertion.get(), "the execution should run on a fork join pool thread");
    assertNull(result);
  }

  public void testAsyncExecutionFailure() {
    AtomicBoolean fjpAssertion = new AtomicBoolean();

    assertThrows(() -> Failsafe.with(retryPolicy).with(executor).getAsync(() -> {
      fjpAssertion.set(Thread.currentThread() instanceof ForkJoinWorkerThread);
      executionCalled.set(true);
      throw new IllegalStateException();
    }).get(), ExecutionException.class, IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertTrue(fjpAssertion.get(), "the execution should run on a fork join pool thread");
  }

  public void testAsyncExecutionThatThrowsFromTheExecutor() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(throwingExecutor).runAsync(() -> {
      executionCalled.set(true);
    }).get(), ExecutionException.class, IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }
}
