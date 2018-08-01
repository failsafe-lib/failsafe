package net.jodah.failsafe.metrics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.Failsafe;
import org.testng.annotations.Test;

@Test
public class MetricsCollectorTest {

  public void runWillMarkSuccess() {
    MetricsCollector collector = mock(MetricsCollector.class);
    CircuitBreaker breaker = new CircuitBreaker()
        .withMetricsCollector(collector);

    Failsafe.with(breaker).run(() -> {});

    verify(collector, times(1)).markSuccess(State.CLOSED, null);
  }
}
