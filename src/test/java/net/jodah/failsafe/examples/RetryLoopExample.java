package net.jodah.failsafe.examples;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.Execution;
import net.jodah.failsafe.RetryPolicy;

@SuppressWarnings("unchecked")
public class RetryLoopExample {
  static List<Object> list;

  static {
    list = mock(List.class);
    when(list.size()).thenThrow(IllegalStateException.class, IllegalStateException.class).thenReturn(5);
  }

  public static void main(String... args) throws Throwable {
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class).withBackoff(10, 40,
        TimeUnit.MILLISECONDS);
    Execution execution = new Execution(retryPolicy);

    while (!execution.isComplete()) {
      try {
        execution.complete(list.size());
      } catch (IllegalStateException e) {
        execution.recordFailure(e);

        // Wait before retrying
        Thread.sleep(execution.getWaitTime().toMillis());
      }
    }

    assertEquals(execution.getLastResult(), Integer.valueOf(5));
    assertEquals(execution.getExecutions(), 3);
  }
}
