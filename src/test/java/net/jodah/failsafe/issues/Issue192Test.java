package net.jodah.failsafe.issues;

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Testing;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
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

  @BeforeTest
  protected void beforeTest() {
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterTest
  protected void afterTest() {
    executor.shutdownNow();
  }

  /**
   * Asserts the handling of multiple retry policies with an async execution.
   */
  public void testAsnc() {
    AtomicInteger exceptionA = new AtomicInteger();
    AtomicInteger exceptionB = new AtomicInteger();
    AtomicInteger exceptionC = new AtomicInteger();
    RetryPolicy<Object> policyA = new RetryPolicy<>().handle(ExceptionA.class)
      .withMaxRetries(5)
      .onRetry(evt -> exceptionA.incrementAndGet());
    RetryPolicy<Object> policyB = new RetryPolicy<>().handle(ExceptionB.class)
      .withMaxRetries(3)
      .onRetry(evt -> exceptionB.incrementAndGet());
    RetryPolicy<Object> policyC = new RetryPolicy<>().handle(ExceptionC.class)
      .withMaxRetries(2)
      .onRetry(evt -> exceptionC.incrementAndGet());

    Asserts.assertThrows(() -> Failsafe.with(policyA, policyB, policyC)
      .getAsyncExecution(
        execution -> Testing.futureException(executor, new ExceptionB()).whenComplete((result, failure) -> {
          if (execution.complete(result, failure))
            ;//System.out.println("Result = " + result + "; failure = " + failure);
          else if (!execution.retry())
            ;//System.out.println("Connection attempts failed " + failure);
        }))
      .get(), ExecutionException.class, ExceptionB.class);

    Assert.assertEquals(exceptionA.get(), 0);
    Assert.assertEquals(exceptionB.get(), 3);
    Assert.assertEquals(exceptionC.get(), 0);
  }
}
