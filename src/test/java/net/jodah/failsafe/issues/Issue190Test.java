package net.jodah.failsafe.issues;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Testing;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class Issue190Test {
  ScheduledExecutorService executor;

  @BeforeTest
  protected void beforeTest() {
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterTest
  protected void afterTest() {
    executor.shutdownNow();
  }

  public void test() throws Throwable {
    RetryPolicy<Object> policy = new RetryPolicy<>().withMaxRetries(5);
    AtomicInteger failureEvents = new AtomicInteger();
    AtomicInteger successEvents = new AtomicInteger();
    Waiter waiter = new Waiter();

    Failsafe.with(policy).onFailure(e -> {
      failureEvents.incrementAndGet();
      waiter.resume();
    }).onSuccess(e -> {
      successEvents.incrementAndGet();
      waiter.resume();
    }).getAsyncExecution(execution -> Testing.futureResult(executor, true).whenComplete((result, failure) -> {
      execution.complete(result);
    })).get();

    waiter.await(1000);
    Assert.assertEquals(failureEvents.get(), 0);
    Assert.assertEquals(successEvents.get(), 1);
  }
}
