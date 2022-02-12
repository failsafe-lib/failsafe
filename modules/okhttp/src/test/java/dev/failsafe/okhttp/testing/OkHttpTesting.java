package dev.failsafe.okhttp.testing;

import dev.failsafe.FailsafeExecutor;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;

import dev.failsafe.okhttp.FailsafeCall;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class OkHttpTesting extends Testing {
  public <T> void testRequest(FailsafeExecutor<Response> failsafe, Call when, Then<Response> then, int expectedStatus,
    T expectedResult) {
    test(failsafe, when, then, expectedStatus, expectedResult, null);
  }

  @SafeVarargs
  public final void testFailure(FailsafeExecutor<Response> failsafe, Call when, Then<Response> then,
    Class<? extends Throwable>... expectedExceptions) {
    test(failsafe, when, then, 0, null, expectedExceptions);
  }

  private <T> void test(FailsafeExecutor<Response> failsafe, Call when, Then<Response> then, int expectedStatus,
    T expectedResult, Class<? extends Throwable>[] expectedExceptions) {
    AtomicReference<CompletableFuture<Response>> futureRef = new AtomicReference<>();
    AtomicReference<ExecutionCompletedEvent<Response>> completedEventRef = new AtomicReference<>();
    Waiter completionListenerWaiter = new Waiter();
    EventListener<ExecutionCompletedEvent<Response>> setCompletedEventFn = e -> {
      completedEventRef.set(e);
      completionListenerWaiter.resume();
    };
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Class<? extends Throwable>[] expectedExInner = expectedExceptions == null ? new Class[] {} : expectedExceptions;
    Collections.addAll(expected, expectedExInner);
    failsafe.onComplete(setCompletedEventFn);

    Runnable postTestFn = () -> {
      ignoreExceptions(() -> completionListenerWaiter.await(5000));
      ExecutionCompletedEvent<Response> completedEvent = completedEventRef.get();
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

    Consumer<Response> assertResult = response -> {
      String result = unwrapExceptions(() -> response.body().string());
      assertEquals(result, expectedResult);
      assertEquals(response.code(), expectedStatus);
    };

    // Run sync test and assert result
    System.out.println("\nRunning sync test");
    FailsafeCall failsafeCall = FailsafeCall.of(when, failsafe);
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
    CompletableFuture<Response> future = failsafeCall.executeAsync();
    futureRef.set(future);
    if (expectedExInner.length == 0) {
      assertResult.accept(unwrapExceptions(future::get));
    } else {
      assertThrowsSup(future::get, expected);
    }
    postTestFn.run();
  }
}
