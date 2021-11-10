package dev.failsafe.issues;

import dev.failsafe.RetryPolicy;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import dev.failsafe.Failsafe;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class Issue190Test {
  ScheduledExecutorService executor;

  @BeforeClass
  protected void beforeClass() {
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterClass
  protected void afterClass() {
    executor.shutdownNow();
  }

  public void test() throws Throwable {
    RetryPolicy<Object> policy = RetryPolicy.builder().withMaxRetries(5).build();
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
      execution.recordResult(result);
    })).get();

    waiter.await(1000);
    Assert.assertEquals(failureEvents.get(), 0);
    Assert.assertEquals(successEvents.get(), 1);
  }
}
