package net.jodah.recurrent.issues;

import static net.jodah.recurrent.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.recurrent.AsyncListeners;
import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RecurrentFuture;
import net.jodah.recurrent.RetryPolicy;

@Test
public class Issue9 {
  public interface Service {
    boolean connect();
  }

  public void test() throws Throwable {
    // Given - Fail twice then succeed
    AtomicInteger retryCounter = new AtomicInteger();
    Service service = mock(Service.class);
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    AsyncListeners<Boolean> listeners = new AsyncListeners<Boolean>().whenRetry((result, failure, stats) -> {
      retryCounter.incrementAndGet();
    });

    // When
    AtomicInteger successCounter = new AtomicInteger();
    RecurrentFuture<Boolean> future = Recurrent
        .get(() -> service.connect(), new RetryPolicy().withMaxRetries(2), executor, listeners).whenSuccess(p -> {
          successCounter.incrementAndGet();
        });

    // Then
    verify(service, times(3)).connect();
    assertEquals(future.get().booleanValue(), true);
    assertEquals(retryCounter.get(), 2);
    assertEquals(successCounter.get(), 1);
  }
}
