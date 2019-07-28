package net.jodah.failsafe;

import net.jodah.failsafe.function.DelayFunction;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
@SuppressWarnings("unchecked")
public class DelayablePolicyTest {
  DelayFunction delay5Millis = (r, f, c) -> Duration.ofMillis(5);

  static class FooPolicy<R> extends DelayablePolicy<FooPolicy<R>, R> {
    @Override
    public PolicyExecutor<Policy<R>> toExecutor(AbstractExecution execution) {
      return null;
    }
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullDelayFunction() {
    new FooPolicy<>().withDelay(null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullResult() {
    new FooPolicy<>().withDelayWhen(delay5Millis, null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullFailureType() {
    new FooPolicy<>().withDelayOn(delay5Millis, null);
  }

  public void shouldComputeDelay() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelay((r, f, c) -> expected);
    assertEquals(policy.computeDelay(ExecutionResult.NONE, null), expected);
  }

  public void shouldComputeDelayForResultValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelayWhen(delay5Millis, true);
    assertEquals(policy.computeDelay(ExecutionResult.success(true), null), expected);
    assertNull(policy.computeDelay(ExecutionResult.success(false), null));
  }

  public void shouldComputeDelayForNegativeValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelay((r, f, c) -> Duration.ofMillis(-1));
    assertNull(policy.computeDelay(ExecutionResult.success(true), null));
  }

  public void shouldComputeDelayForFailureType() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelayOn(delay5Millis, IllegalStateException.class);
    assertEquals(policy.computeDelay(ExecutionResult.failure(new IllegalStateException()), null), expected);
    assertNull(policy.computeDelay(ExecutionResult.failure(new IllegalArgumentException()), null));
  }
}
