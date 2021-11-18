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
package dev.failsafe;

import dev.failsafe.function.ContextualSupplier;
import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.testing.Mocking.FooPolicy;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
public class DelayablePolicyTest {
  ContextualSupplier<Object, Duration> delay5Millis = ctx -> Duration.ofMillis(5);

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullDelayFunction() {
    FooPolicy.builder().withDelay(null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullResult() {
    FooPolicy.builder().withDelayFnWhen(delay5Millis, null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullFailureType() {
    FooPolicy.builder().withDelayFnOn(delay5Millis, null);
  }

  public void shouldComputeDelay() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelayFn(ctx -> expected).build();
    assertEquals(policy.computeDelay(execOfResult(null)), expected);
  }

  public void shouldComputeDelayForResultValue() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelayFnWhen(delay5Millis, true).build();
    assertEquals(policy.computeDelay(execOfResult(true)), expected);
    assertNull(policy.computeDelay(execOfResult(false)));
  }

  public void shouldComputeDelayForNegativeValue() {
    FooPolicy<Object> policy = FooPolicy.builder().withDelayFn(ctx -> Duration.ofMillis(-1)).build();
    assertNull(policy.computeDelay(execOfResult(true)));
  }

  public void shouldComputeDelayForFailureType() {
    Duration expected = Duration.ofMillis(5);
    FooPolicy<Object> policy = FooPolicy.builder().withDelayFnOn(delay5Millis, IllegalStateException.class).build();
    assertEquals(policy.computeDelay(execOfFailure(new IllegalStateException())), expected);
    assertNull(policy.computeDelay(execOfFailure(new IllegalArgumentException())));
  }

  static <R> ExecutionContext<R> execOfResult(R result) {
    return new ExecutionImpl<>(ExecutionResult.success(result));
  }

  static <R> ExecutionContext<R> execOfFailure(Throwable failure) {
    return new ExecutionImpl<>(ExecutionResult.failure(failure));
  }
}
