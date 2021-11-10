package dev.failsafe.issues;

import dev.failsafe.testing.Asserts;
import dev.failsafe.testing.Testing;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class Issue192Test {
  ScheduledExecutorService executor;

  static class ExceptionA extends Exception {
  }

  static class ExceptionB extends Exception {
  }

  static class ExceptionC extends Exception {
  }

  @BeforeClass
  protected void beforeClass() {
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterClass
  protected void afterClass() {
    executor.shutdownNow();
  }

  /**
   * Asserts the handling of multiple retry policies with an async execution.
   */
  public void testAsync() {
    AtomicInteger exceptionA = new AtomicInteger();
    AtomicInteger exceptionB = new AtomicInteger();
    AtomicInteger exceptionC = new AtomicInteger();
    RetryPolicy<Object> policyA = RetryPolicy.builder()
      .handle(ExceptionA.class)
      .withMaxRetries(5)
      .onRetry(evt -> exceptionA.incrementAndGet())
      .build();
    RetryPolicy<Object> policyB = RetryPolicy.builder()
      .handle(ExceptionB.class)
      .withMaxRetries(3)
      .onRetry(evt -> exceptionB.incrementAndGet())
      .build();
    RetryPolicy<Object> policyC = RetryPolicy.builder()
      .handle(ExceptionC.class)
      .withMaxRetries(2)
      .onRetry(evt -> exceptionC.incrementAndGet())
      .build();

    Asserts.assertThrows(() -> Failsafe.with(policyA, policyB, policyC)
      .getAsyncExecution(
        execution -> Testing.futureException(executor, new ExceptionB()).whenComplete((result, failure) -> {
          //System.out.println("Result = " + result + "; failure = " + failure);
          execution.record(result, failure);
        }))
      .get(), ExecutionException.class, ExceptionB.class);

    Assert.assertEquals(exceptionA.get(), 0);
    Assert.assertEquals(exceptionB.get(), 3);
    Assert.assertEquals(exceptionC.get(), 0);
  }
}
