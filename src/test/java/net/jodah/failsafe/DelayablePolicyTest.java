package net.jodah.failsafe;

import net.jodah.failsafe.function.DelayFunction;
import net.jodah.failsafe.testing.Mocking.FooPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
@SuppressWarnings("unchecked")
public class DelayablePolicyTest {
  DelayFunction delay5Millis = (r, f, c) -> Duration.ofMillis(5);

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullDelayFunction() {
    FooPolicy.builder().withDelay((Duration) null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullResult() {
    FooPolicy.builder().withDelayWhen(delay5Millis, null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullFailureType() {
    FooPolicy.builder().withDelayOn(delay5Millis, null);
  }

  public void shouldComputeDelay() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelay((r, f, c) -> expected).build();
    assertEquals(policy.computeDelay(ExecutionContext.ofResult(null)), expected);
  }

  public void shouldComputeDelayForResultValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelayWhen(delay5Millis, true).build();
    assertEquals(policy.computeDelay(ExecutionContext.ofResult(true)), expected);
    assertNull(policy.computeDelay(ExecutionContext.ofResult(false)));
  }

  public void shouldComputeDelayForNegativeValue() {
    FooPolicy<Object> policy = FooPolicy.builder().withDelay((r, f, c) -> Duration.ofMillis(-1)).build();
    assertNull(policy.computeDelay(ExecutionContext.ofResult(true)));
  }

  public void shouldComputeDelayForFailureType() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelayOn(delay5Millis, IllegalStateException.class).build();
    assertEquals(policy.computeDelay(ExecutionContext.ofFailure(new IllegalStateException())), expected);
    assertNull(policy.computeDelay(ExecutionContext.ofFailure(new IllegalArgumentException())));
  }
}
