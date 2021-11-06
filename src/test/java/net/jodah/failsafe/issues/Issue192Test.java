package net.jodah.failsafe.issues;

import net.jodah.failsafe.testing.Asserts;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.testing.Testing;
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
      .build()
      .onRetry(evt -> exceptionA.incrementAndGet());
    RetryPolicy<Object> policyB = RetryPolicy.builder()
      .handle(ExceptionB.class)
      .withMaxRetries(3)
      .build()
      .onRetry(evt -> exceptionB.incrementAndGet());
    RetryPolicy<Object> policyC = RetryPolicy.builder()
      .handle(ExceptionC.class)
      .withMaxRetries(2)
      .build()
      .onRetry(evt -> exceptionC.incrementAndGet());

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
