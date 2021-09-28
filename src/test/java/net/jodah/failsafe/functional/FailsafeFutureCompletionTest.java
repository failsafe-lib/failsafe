package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertFalse;

/**
 * Tests behavior when a FailsafeFuture is explicitly completed.
 */
@Test
public class FailsafeFutureCompletionTest extends Testing {
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
      waiter.assertTrue(e.getFailure() instanceof IllegalArgumentException);
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
