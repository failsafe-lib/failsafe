package net.jodah.failsafe.internal;

import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class RetryPolicyImplTest {
  public void testIsAbortableNull() {
    RetryPolicyImpl<Object> policy = (RetryPolicyImpl<Object>) RetryPolicy.builder().build();
    assertFalse(policy.isAbortable(null, null));
  }

  public void testIsAbortableCompletionPredicate() {
    RetryPolicyImpl<Object> policy = (RetryPolicyImpl<Object>) RetryPolicy.builder()
      .abortIf((result, failure) -> result == "test" || failure instanceof IllegalArgumentException)
      .build();
    assertTrue(policy.isAbortable("test", null));
    assertFalse(policy.isAbortable(0, null));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testIsAbortableFailurePredicate() {
    RetryPolicyImpl<Object> policy = (RetryPolicyImpl<Object>) RetryPolicy.builder()
      .abortOn(failure -> failure instanceof ConnectException)
      .build();
    assertTrue(policy.isAbortable(null, new ConnectException()));
    assertFalse(policy.isAbortable(null, new IllegalArgumentException()));
  }

  public void testIsAbortablePredicate() {
    RetryPolicyImpl<Integer> policy = (RetryPolicyImpl<Integer>) RetryPolicy.<Integer>builder()
      .abortIf(result -> result > 100)
      .build();
    assertTrue(policy.isAbortable(110, null));
    assertFalse(policy.isAbortable(50, null));
    assertFalse(policy.isAbortable(50, new IllegalArgumentException()));
  }

  public void testIsAbortableFailure() {
    RetryPolicyImpl<Object> policy = (RetryPolicyImpl<Object>) RetryPolicy.builder().abortOn(Exception.class).build();
    assertTrue(policy.isAbortable(null, new Exception()));
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));

    policy = (RetryPolicyImpl<Object>) RetryPolicy.builder()
      .abortOn(IllegalArgumentException.class, IOException.class)
      .build();
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertTrue(policy.isAbortable(null, new IOException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));

    policy = (RetryPolicyImpl<Object>) RetryPolicy.builder()
      .abortOn(Arrays.asList(IllegalArgumentException.class))
      .build();
    assertTrue(policy.isAbortable(null, new IllegalArgumentException()));
    assertFalse(policy.isAbortable(null, new RuntimeException()));
    assertFalse(policy.isAbortable(null, new IllegalStateException()));
  }

  public void testIsAbortableResult() {
    RetryPolicyImpl<Object> policy = (RetryPolicyImpl<Object>) RetryPolicy.builder().abortWhen(10).build();
    assertTrue(policy.isAbortable(10, null));
    assertFalse(policy.isAbortable(5, null));
    assertFalse(policy.isAbortable(5, new IllegalArgumentException()));
  }
}
