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
package net.jodah.failsafe.internal.util;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.internal.util.ReentrantCircuit;
import static net.jodah.failsafe.Testing.*;

@Test
public class ReentrantCircuitTest {
  ReentrantCircuit circuit;

  @BeforeMethod
  protected void beforeMethod() {
    circuit = new ReentrantCircuit();
  }

  public void shouldInitiallyBeClosed() {
    assertTrue(circuit.isClosed());
  }

  public void shouldHandleOpenCloseCycles() {
    for (int i = 0; i < 3; i++) {
      circuit.open();
      circuit.close();
    }

    assertTrue(circuit.isClosed());
  }

  public void shouldHandleRepeatedOpens() {
    for (int i = 0; i < 3; i++)
      circuit.open();

    assertFalse(circuit.isClosed());
  }

  public void shouldHandleRepeatedClosed() {
    for (int i = 0; i < 3; i++)
      circuit.close();

    assertTrue(circuit.isClosed());
  }

  public void shouldReturnWhenAwaitAndAlreadyClosed() {
    long t = System.currentTimeMillis();
    circuit.await();
    circuit.await(3, TimeUnit.MINUTES);

    // Awaits should return immediately
    assertTrue(System.currentTimeMillis() - t < 500);
  }

  public void shouldHandleSequentialWaiters() throws Throwable {
    final Waiter waiter = new Waiter();
    for (int i = 0; i < 1; i++) {
      circuit.open();

      runInThread(() -> {
        circuit.await();
        waiter.resume();
      });

      Thread.sleep(500);
      circuit.close();
      waiter.await(500);
    }
  }

  public void shouldHandleConcurrentWaiters() throws Throwable {
    circuit.open();

    final Waiter waiter = new Waiter();
    for (int i = 0; i < 3; i++)
      runInThread(() -> {
        circuit.await();
        waiter.resume();
      });

    Thread.sleep(1000);
    circuit.close();
    waiter.await(500);
  }

  public void shouldNotBlockOpenWhenSyncAcquired() throws Throwable {
    circuit.open();

    final Waiter waiter = new Waiter();
    runInThread(() -> {
      circuit.await();
      waiter.resume();
    });

    Thread.sleep(300);
    circuit.open();
    circuit.close();
    waiter.await(500);
  }
}