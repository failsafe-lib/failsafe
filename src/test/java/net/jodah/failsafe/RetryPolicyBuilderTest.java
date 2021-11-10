/*
 * Copyright 2016 the original author or authors.
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
package net.jodah.failsafe;

import net.jodah.failsafe.testing.Asserts;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.testng.AssertJUnit.assertEquals;

@Test
public class RetryPolicyBuilderTest {
  public void shouldRequireValidBackoff() {
    Asserts.assertThrows(() -> RetryPolicy.builder().withBackoff(0, 0, null), NullPointerException.class);
    Asserts.assertThrows(
      () -> RetryPolicy.builder().withMaxDuration(Duration.ofMillis(1)).withBackoff(100, 120, ChronoUnit.MILLIS),
      IllegalStateException.class);
    Asserts.assertThrows(() -> RetryPolicy.builder().withBackoff(-3, 10, ChronoUnit.MILLIS),
      IllegalArgumentException.class);
    Asserts.assertThrows(() -> RetryPolicy.builder().withBackoff(100, 10, ChronoUnit.MILLIS),
      IllegalArgumentException.class);
    Asserts.assertThrows(() -> RetryPolicy.builder().withBackoff(5, 10, ChronoUnit.MILLIS, .5),
      IllegalArgumentException.class);
  }

  public void shouldRequireValidDelay() {
    Asserts.assertThrows(() -> RetryPolicy.builder().withDelay((Duration) null), NullPointerException.class);
    Asserts.assertThrows(
      () -> RetryPolicy.builder().withMaxDuration(Duration.ofMillis(1)).withDelay(Duration.ofMillis(100)),
      IllegalStateException.class);
    Asserts.assertThrows(
      () -> RetryPolicy.builder().withBackoff(1, 2, ChronoUnit.MILLIS).withDelay(Duration.ofMillis(100)),
      IllegalStateException.class);
    Asserts.assertThrows(() -> RetryPolicy.builder().withDelay(Duration.ofMillis(-1)), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxRetries() {
    Asserts.assertThrows(() -> RetryPolicy.builder().withMaxRetries(-4), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxDuration() {
    Asserts.assertThrows(
      () -> RetryPolicy.builder().withDelay(Duration.ofMillis(100)).withMaxDuration(Duration.ofMillis(100)),
      IllegalStateException.class);
  }

  public void shouldConfigureRandomDelay() {
    RetryPolicy<Object> rp = RetryPolicy.builder().withDelay(1, 10, ChronoUnit.NANOS).build();
    assertEquals(rp.getConfig().getDelayMin().toNanos(), 1);
    assertEquals(rp.getConfig().getDelayMax().toNanos(), 10);
  }

  public void testConfigureMaxAttempts() {
    assertEquals(RetryPolicy.builder().withMaxRetries(-1).build().getConfig().getMaxAttempts(), -1);
    assertEquals(RetryPolicy.builder().withMaxRetries(0).build().getConfig().getMaxAttempts(), 1);
    assertEquals(RetryPolicy.builder().withMaxRetries(1).build().getConfig().getMaxAttempts(), 2);
  }
}
