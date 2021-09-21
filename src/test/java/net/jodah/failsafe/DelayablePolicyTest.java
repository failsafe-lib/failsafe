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
    public PolicyExecutor<R, Policy<R>> toExecutor(int policyIndex) {
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
    assertEquals(policy.computeDelay(ExecutionContext.ofResult(null)), expected);
  }

  public void shouldComputeDelayForResultValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelayWhen(delay5Millis, true);
    assertEquals(policy.computeDelay(ExecutionContext.ofResult(true)), expected);
    assertNull(policy.computeDelay(ExecutionContext.ofResult(false)));
  }

  public void shouldComputeDelayForNegativeValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelay((r, f, c) -> Duration.ofMillis(-1));
    assertNull(policy.computeDelay(ExecutionContext.ofResult(true)));
  }

  public void shouldComputeDelayForFailureType() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy policy = new FooPolicy<>().withDelayOn(delay5Millis, IllegalStateException.class);
    assertEquals(policy.computeDelay(ExecutionContext.ofFailure(new IllegalStateException())), expected);
    assertNull(policy.computeDelay(ExecutionContext.ofFailure(new IllegalArgumentException())));
  }
}
