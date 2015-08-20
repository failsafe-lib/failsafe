package net.jodah.recurrent.examples;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import net.jodah.recurrent.RetryPolicy;
import net.jodah.recurrent.Invocation;

@Test
public class RetryLoopExample {
  List<Object> list;

  @SuppressWarnings("unchecked")
  @BeforeClass
  void beforeClass() {
    list = mock(List.class);
    when(list.size()).thenThrow(IllegalStateException.class, IllegalStateException.class).thenReturn(5);
  }

  @SuppressWarnings("unchecked")
  public void retryLoopExample() throws Throwable {
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class).withBackoff(10, 40,
        TimeUnit.MILLISECONDS);
    Invocation invocation = new Invocation(retryPolicy);

    while (!invocation.isComplete()) {
      try {
        invocation.complete(list.size());
      } catch (IllegalStateException e) {
        invocation.recordFailure(e);

        // Wait before retrying
        Thread.sleep(invocation.getWaitMillis());
      }
    }

    assertEquals(invocation.getLastResult(), Integer.valueOf(5));
    assertEquals(invocation.getAttemptCount(), 3);
  }
}
