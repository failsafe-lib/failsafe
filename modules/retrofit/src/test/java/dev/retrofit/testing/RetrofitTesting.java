package dev.retrofit.testing;

import dev.failsafe.FailsafeExecutor;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.retrofit.FailsafeCall;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import retrofit2.Call;
import retrofit2.Response;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class RetrofitTesting extends Testing {
  public <T> void testRequest(FailsafeExecutor<Response<T>> failsafe, Call<T> when, Then<Response<T>> then,
    int expectedStatus, T expectedResult) {
    test(failsafe, when, then, expectedStatus, expectedResult, null);
  }

  @SafeVarargs
  public final <T> void testFailure(FailsafeExecutor<Response<T>> failsafe, Call<T> when, Then<Response<T>> then,
    Class<? extends Throwable>... expectedExceptions) {
    test(failsafe, when, then, 0, null, expectedExceptions);
  }

  private <T> void test(FailsafeExecutor<Response<T>> failsafe, Call<T> when, Then<Response<T>> then,
    int expectedStatus, T expectedResult, Class<? extends Throwable>[] expectedExceptions) {
    AtomicReference<CompletableFuture<Response<T>>> futureRef = new AtomicReference<>();
    AtomicReference<ExecutionCompletedEvent<Response<T>>> completedEventRef = new AtomicReference<>();
    Waiter completionListenerWaiter = new Waiter();
    EventListener<ExecutionCompletedEvent<Response<T>>> setCompletedEventFn = e -> {
      completedEventRef.set(e);
      completionListenerWaiter.resume();
    };
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Class<? extends Throwable>[] expectedExInner = expectedExceptions == null ? new Class[] {} : expectedExceptions;
    Collections.addAll(expected, expectedExInner);
    failsafe.onComplete(setCompletedEventFn);

    Runnable postTestFn = () -> {
      ignoreExceptions(() -> completionListenerWaiter.await(5000));
      ExecutionCompletedEvent<Response<T>> completedEvent = completedEventRef.get();
      if (expectedExceptions == null) {
        assertEquals(completedEvent.getResult().code(), expectedStatus);
        assertNull(completedEvent.getException());
      } else {
        assertNull(completedEvent.getResult());
        assertMatches(completedEvent.getException(), expectedExceptions);
      }
      if (then != null)
        then.accept(futureRef.get(), completedEvent);
    };

    Consumer<Response<T>> assertResult = response -> {
      T result = unwrapExceptions(response::body);
      assertEquals(result, expectedResult);
      assertEquals(response.code(), expectedStatus);
    };

    // Run sync test and assert result
    System.out.println("\nRunning sync test");
    FailsafeCall<T> failsafeCall = FailsafeCall.with(failsafe).compose(when);
    if (expectedExceptions == null) {
      assertResult.accept(unwrapExceptions(failsafeCall::execute));
    } else {
      assertThrows(failsafeCall::execute, expectedExceptions);
    }
    postTestFn.run();

    if (expectedExInner.length > 0)
      expected.add(0, ExecutionException.class);

    // Run async test and assert result
    System.out.println("\nRunning async test");
    failsafeCall = failsafeCall.clone();
    CompletableFuture<Response<T>> future = failsafeCall.executeAsync();
    futureRef.set(future);
    if (expectedExInner.length == 0) {
      assertResult.accept(unwrapExceptions(future::get));
    } else {
      assertThrowsSup(future::get, expected);
    }
    postTestFn.run();
  }
}
