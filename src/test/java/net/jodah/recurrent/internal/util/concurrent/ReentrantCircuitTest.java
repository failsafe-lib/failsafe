package net.jodah.recurrent.internal.util.concurrent;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

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

      new Thread(new Runnable() {
        @Override
        public void run() {
          circuit.await();
          waiter.resume();
        }
      }).start();

      Thread.sleep(500);
      circuit.close();
      waiter.await(500);
    }
  }

  public void shouldHandleConcurrentWaiters() throws Throwable {
    circuit.open();

    final Waiter waiter = new Waiter();
    waiter.expectResumes(3);
    for (int i = 0; i < 3; i++)
      new Thread(new Runnable() {
        @Override
        public void run() {
          circuit.await();
          waiter.resume();
        }
      }).start();

    Thread.sleep(1000);
    circuit.close();
    waiter.await(500);
  }

  public void shouldInterruptWaiters() throws Throwable {
    circuit.open();

    final Waiter waiter = new Waiter();
    waiter.expectResumes(3);
    for (int i = 0; i < 3; i++)
      new Thread(new Runnable() {
        @Override
        public void run() {
          circuit.await();
          waiter.resume();
        }
      }).start();

    Thread.sleep(300);
    circuit.interruptWaiters();
    waiter.await(500);
  }

  public void shouldNotBlockOpenWhenSyncAcquired() throws Throwable {
    circuit.open();

    final Waiter waiter = new Waiter();
    waiter.expectResume();
    new Thread(new Runnable() {
      @Override
      public void run() {
        circuit.await();
        waiter.resume();
      }
    }).start();

    Thread.sleep(300);
    circuit.open();
    circuit.close();
    waiter.await(500);
  }
}
