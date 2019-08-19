package net.jodah.failsafe.functional;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Execution;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the handling of ordered policy execution via an Execution or AsyncExecution.
 */
@Test
public class ExecutionPolicyOrderingTest {
  public void testRetryPolicyAndCircuitBreaker() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);

    Execution execution = new Execution(rp, cb);
    execution.recordFailure(new Exception());
    execution.recordFailure(new Exception());
    assertFalse(execution.isComplete());
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }

  public void testCircuitBreakerAndRetryPolicy() {
    RetryPolicy rp = new RetryPolicy().withMaxRetries(1);
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);

    Execution execution = new Execution(cb, rp);
    execution.recordFailure(new Exception());
    assertFalse(execution.isComplete());
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }
}
