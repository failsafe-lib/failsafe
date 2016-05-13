package net.jodah.failsafe.issues;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.AsyncListeners;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.RetryPolicy;

@Test
public class Issue9 {
  public interface Service {
    boolean connect();
  }

  public void test() throws Throwable {
    // Given - Fail twice then succeed
    AtomicInteger retryCounter = new AtomicInteger();
    Service service = mock(Service.class);
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(true);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    AsyncListeners<Boolean> listeners = new AsyncListeners<Boolean>().onRetry((result, failure, context) -> {
      retryCounter.incrementAndGet();
    });
    Waiter waiter = new Waiter();

    // When
    AtomicInteger successCounter = new AtomicInteger();
    FailsafeFuture<Boolean> future = Failsafe.with(new RetryPolicy().withMaxRetries(2))
        .with(executor)
        .with(listeners)
        .get(() -> service.connect())
        .onSuccess(p -> {
          successCounter.incrementAndGet();
          waiter.resume();
        });

    // Then
    waiter.await(1000);
    verify(service, times(3)).connect();
    assertEquals(future.get().booleanValue(), true);
    assertEquals(retryCounter.get(), 2);
    assertEquals(successCounter.get(), 1);
  }
}
