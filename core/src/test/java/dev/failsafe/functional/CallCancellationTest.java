package dev.failsafe.functional;

import dev.failsafe.*;
import dev.failsafe.function.CheckedConsumer;
import dev.failsafe.function.ContextualRunnable;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertTrue;

@Test
public class CallCancellationTest extends Testing {
  Waiter waiter;

  @BeforeMethod
  void beforeMethod() {
    waiter = new Waiter();
  }

  private void assertCancel(FailsafeExecutor<Void> executor, ContextualRunnable<Void> runnable,
    boolean testWithoutInterrupt) throws Throwable {
    CheckedConsumer<Boolean> test = interrupt -> {
      // Given
      Call<Void> call = executor.onComplete(e -> {
        waiter.assertNull(e.getResult());
        if (!interrupt)
          waiter.assertNull(e.getException());
        else
          waiter.assertTrue(e.getException() instanceof InterruptedException);
        waiter.resume();
      }).newCall(runnable);

      // When
      runInThread(() -> {
        Testing.sleep(300);
        waiter.assertTrue(call.cancel(interrupt));
      });
      if (!interrupt)
        call.execute();
      else
        assertThrows(call::execute, FailsafeException.class, InterruptedException.class);

      waiter.await(1000);

      // Then
      assertTrue(call.isCancelled());
    };

    // Test without interrupt
    if (testWithoutInterrupt)
      test.accept(false);

    // Test with interrupt
    test.accept(true);
  }

  public void shouldCancelRetriesWithBlockedExecution() throws Throwable {
    assertCancel(Failsafe.with(RetryPolicy.ofDefaults()), ctx -> {
      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        throw e;
      }
    }, true);
  }

  public void shouldCancelRetriesWithPendingDelay() throws Throwable {
    RetryPolicy<Void> retryPolicy = RetryPolicy.<Void>builder().withDelay(Duration.ofMinutes(1)).build();
    assertCancel(Failsafe.with(retryPolicy), ctx -> {
      throw new IllegalStateException();
    }, false);
  }

  public void shouldPropagateCancelToCallback() throws Throwable {
    AtomicBoolean callbackCalled = new AtomicBoolean();
    assertCancel(Failsafe.with(RetryPolicy.ofDefaults()), ctx -> {
      ctx.onCancel(() -> callbackCalled.set(true));

      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        waiter.assertTrue(callbackCalled.get());
        throw e;
      }
    }, true);
  }
}

