/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe.internal;

import dev.failsafe.RetryPolicy;
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
