package net.jodah.recurrent;

import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

@Test
public class AbstractRecurrentTest {
  RetryPolicy retryAlways = new RetryPolicy();
  RetryPolicy retryNever = new RetryPolicy().withMaxRetries(0);
  RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  Service service = mock(Service.class);
  AtomicInteger counter;

  public interface Service {
    boolean connect();

    boolean disconnect();
  }
}
