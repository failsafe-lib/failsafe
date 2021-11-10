package net.jodah.failsafe;

import net.jodah.failsafe.testing.Mocking.FooPolicy;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class FailurePolicyTest {
  public void testIsFailureForNull() {
    FooPolicy<Object> policy = FooPolicy.builder().build();
    assertFalse(policy.isFailure(null, null));
  }

  public void testIsFailureForFailurePredicate() {
    FooPolicy<Object> policy = FooPolicy.builder().handleIf(failure -> failure instanceof ConnectException).build();
    assertTrue(policy.isFailure(null, new ConnectException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureForResultPredicate() {
    FooPolicy<Integer> policy = FooPolicy.<Integer>builder().handleResultIf(result -> result > 100).build();
    assertTrue(policy.isFailure(110, null));
    assertFalse(policy.isFailure(50, null));
  }

  public void testIsFailureCompletionPredicate() {
    FooPolicy<Object> policy = FooPolicy.builder()
      .handleIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException)
      .build();
    assertTrue(policy.isFailure("test", null));
    // No retries needed for successful result
    assertFalse(policy.isFailure(0, null));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIgnoresThrowingPredicate() {
    FooPolicy<Integer> policy = FooPolicy.<Integer>builder().handleIf((result, failure) -> {
      throw new NullPointerException();
    }).build();
    assertFalse(policy.isFailure(1, null));
  }

  @Test(expectedExceptions = OutOfMemoryError.class)
  public void testThrowsFatalErrors() {
    FooPolicy<String> policy = FooPolicy.<String>builder().handleIf((result, failure) -> {
      throw new OutOfMemoryError();
    }).build();
    policy.isFailure("result", null);
  }

  public void testIsFailureForFailure() {
    FooPolicy<Object> policy = FooPolicy.builder().build();
    assertTrue(policy.isFailure(null, new Exception()));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));

    policy = FooPolicy.builder().handle(Exception.class).build();
    assertTrue(policy.isFailure(null, new Exception()));
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));

    policy = FooPolicy.builder().handle(IllegalArgumentException.class, IOException.class).build();
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertTrue(policy.isFailure(null, new IOException()));
    assertFalse(policy.isFailure(null, new RuntimeException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));

    policy = FooPolicy.builder().handle(Arrays.asList(IllegalArgumentException.class)).build();
    assertTrue(policy.isFailure(null, new IllegalArgumentException()));
    assertFalse(policy.isFailure(null, new RuntimeException()));
    assertFalse(policy.isFailure(null, new IllegalStateException()));
  }

  public void testIsFailureForResult() {
    FooPolicy<Integer> policy = FooPolicy.<Integer>builder().handleResult(10).build();
    assertTrue(policy.isFailure(10, null));
    assertFalse(policy.isFailure(5, null));
  }
}
