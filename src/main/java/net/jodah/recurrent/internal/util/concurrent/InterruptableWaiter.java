package net.jodah.recurrent.internal.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A waiter where waiting threads can be interrupted (as opposed to awakened).
 * 
 * @author Jonathan Halterman
 */
public class InterruptableWaiter {
  private final Sync sync = new Sync();

  private static final class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 4016766900138538852L;

    @Override
    protected int tryAcquireShared(int acquires) {
      // Disallow acquisition
      return -1;
    }
  }

  /**
   * Waits forever, aborting if interrupted.
   */
  public void await() throws InterruptedException {
    sync.acquireSharedInterruptibly(0);
  }

  /**
   * Waits for the {@code waitDuration} until the waiter has been interrupted or the operation times out. Returns true
   * if the waiter was interrupted else false if the operation timed out.
   */
  public boolean await(long waitDuration, TimeUnit timeUnit) throws InterruptedException {
    return sync.tryAcquireSharedNanos(0, timeUnit.toNanos(waitDuration));
  }

  /**
   * Interrupts waiting threads.
   */
  public void interruptWaiters() {
    for (Thread t : sync.getSharedQueuedThreads())
      t.interrupt();
  }
}