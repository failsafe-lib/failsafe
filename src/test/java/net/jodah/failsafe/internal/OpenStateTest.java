package net.jodah.failsafe.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.OpenState;

@Test
public class OpenStateTest {
  public void testAllowsExecution() throws Throwable {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withDelay(100, TimeUnit.MILLISECONDS);
    breaker.open();
    OpenState state = new OpenState(breaker);
    assertTrue(breaker.isOpen());
    assertFalse(state.allowsExecution(null));

    // When
    Thread.sleep(110);

    // Then
    assertTrue(state.allowsExecution(null));
    assertEquals(breaker.getState(), State.HALF_OPEN);
  }
}
