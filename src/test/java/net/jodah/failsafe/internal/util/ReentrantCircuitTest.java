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

  public void shouldReturnWhenAwaitAndAlreadyClosed() throws Throwable {
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