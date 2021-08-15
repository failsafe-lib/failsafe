package net.jodah.failsafe.issues;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.Timeout;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.time.Duration;

import static org.testng.Assert.assertNull;

@Test
public class Issue267Test {
  public void test() {
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1000L));
    Fallback<Object> notFoundFallback = Fallback.of(this::handleNotFound).handleIf(this::causedBy404);
    Fallback<Object> failureHandling = Fallback.ofException(this::handleException);

    Integer result = Failsafe.with(failureHandling, notFoundFallback, timeout).get(this::connect);
    assertNull(result);
  }

  private Integer connect(ExecutionContext<Integer> context) throws ConnectException {
    throw new ConnectException();
  }

  private boolean causedBy404(Object o, Throwable throwable) {
    return throwable instanceof ConnectException;
  }

  private Object handleNotFound(ExecutionAttemptedEvent<?> event) {
    return null;
  }

  private Exception handleException(ExecutionAttemptedEvent<?> event) {
    return new IllegalArgumentException();
  }
}
