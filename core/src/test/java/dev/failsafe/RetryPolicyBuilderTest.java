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
package dev.failsafe;

import dev.failsafe.testing.Asserts;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.testng.Assert.*;

@Test
public class RetryPolicyBuilderTest extends Asserts {
  public void shouldRequireValidDelay() {
    assertThrows(() -> RetryPolicy.builder().withDelay(null), NullPointerException.class);
    assertThrows(() -> RetryPolicy.builder().withMaxDuration(Duration.ofMillis(1)).withDelay(Duration.ofMillis(100)),
      IllegalStateException.class);
    assertThrows(() -> RetryPolicy.builder().withDelay(Duration.ofMillis(-1)), IllegalArgumentException.class);
    assertThrows(() -> RetryPolicy.builder().withJitter(Duration.ofMillis(7)).withDelay(Duration.ofMillis(5)),
      IllegalStateException.class);
  }

  public void shouldRequireValidBackoff() {
    assertThrows(() -> RetryPolicy.builder().withBackoff(0, 0, null), NullPointerException.class);
    assertThrows(() -> RetryPolicy.builder().withBackoff(-3, 10, ChronoUnit.MILLIS), IllegalArgumentException.class);
    assertThrows(() -> RetryPolicy.builder().withBackoff(100, 10, ChronoUnit.MILLIS), IllegalArgumentException.class);
    assertThrows(() -> RetryPolicy.builder().withBackoff(5, 10, ChronoUnit.MILLIS, .5), IllegalArgumentException.class);
    assertThrows(
      () -> RetryPolicy.builder().withMaxDuration(Duration.ofMillis(1)).withBackoff(100, 120, ChronoUnit.MILLIS),
      IllegalStateException.class);
    assertThrows(() -> RetryPolicy.builder()
      .withJitter(Duration.ofMillis(7))
      .withBackoff(Duration.ofMillis(5), Duration.ofMillis(10)), IllegalStateException.class);
  }

  public void shouldRequireValidRandomDelay() {
    assertThrows(() -> RetryPolicy.builder().withDelay(null, null), NullPointerException.class);
    assertThrows(() -> RetryPolicy.builder().withDelay(Duration.ZERO, Duration.ZERO), IllegalArgumentException.class);
    assertThrows(() -> RetryPolicy.builder().withDelay(Duration.ofMillis(10), Duration.ofMillis(5)),
      IllegalArgumentException.class);
    assertThrows(() -> RetryPolicy.builder()
      .withMaxDuration(Duration.ofMillis(7))
      .withDelay(Duration.ofMillis(5), Duration.ofMillis(10)), IllegalStateException.class);
    assertThrows(() -> RetryPolicy.builder()
      .withJitter(Duration.ofMillis(7))
      .withDelay(Duration.ofMillis(5), Duration.ofMillis(10)), IllegalStateException.class);
  }

  public void shouldRequireValidMaxRetries() {
    assertThrows(() -> RetryPolicy.builder().withMaxRetries(-4), IllegalArgumentException.class);
  }

  public void shouldRequireValidMaxDuration() {
    assertThrows(() -> RetryPolicy.builder().withDelay(Duration.ofMillis(10)).withMaxDuration(Duration.ofMillis(5)),
      IllegalStateException.class);
    assertThrows(() -> RetryPolicy.builder()
      .withDelay(Duration.ofMillis(1), Duration.ofMillis(10))
      .withMaxDuration(Duration.ofMillis(5)), IllegalStateException.class);
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

  public void shouldReplaceWithFixedDelay() {
    // Replace backoff with fixed delay
    RetryPolicyBuilder<Object> rpb = RetryPolicy.builder()
      .withBackoff(Duration.ofMillis(1), Duration.ofMillis(10))
      .withDelay(Duration.ofMillis(5));
    assertEquals(rpb.config.delay, Duration.ofMillis(5));
    assertNull(rpb.config.maxDelay);

    // Replace random with fixed delay
    rpb = RetryPolicy.builder().withDelay(Duration.ofMillis(1), Duration.ofMillis(10)).withDelay(Duration.ofMillis(5));
    assertEquals(rpb.config.delay, Duration.ofMillis(5));
    assertNull(rpb.config.delayMin);
    assertNull(rpb.config.delayMax);
  }

  public void shouldReplaceWithBackoffDelay() {
    // Replace fixed with backoff delay
    RetryPolicyBuilder<Object> rpb = RetryPolicy.builder()
      .withDelay(Duration.ofMillis(5))
      .withBackoff(Duration.ofMillis(1), Duration.ofMillis(10));
    assertEquals(rpb.config.delay, Duration.ofMillis(1));

    // Replace random with backoff delay
    rpb = RetryPolicy.builder()
      .withDelay(Duration.ofMillis(5), Duration.ofMillis(15))
      .withBackoff(Duration.ofMillis(1), Duration.ofMillis(10));
    assertEquals(rpb.config.delay, Duration.ofMillis(1));
    assertNull(rpb.config.delayMin);
    assertNull(rpb.config.delayMax);
  }

  public void shouldReplaceWithRandomDelay() {
    // Replace fixed with random delay
    RetryPolicyBuilder<Object> rpb = RetryPolicy.builder()
      .withDelay(Duration.ofMillis(5))
      .withDelay(Duration.ofMillis(1), Duration.ofMillis(10));
    assertEquals(rpb.config.delay, Duration.ZERO);

    // Replace backoff with random delay
    rpb = RetryPolicy.builder()
      .withBackoff(Duration.ofMillis(5), Duration.ofMillis(15))
      .withDelay(Duration.ofMillis(1), Duration.ofMillis(10));
    assertEquals(rpb.config.delay, Duration.ZERO);
    assertNull(rpb.config.maxDelay);
  }

  public void shouldReplaceWithJitterDuration() {
    RetryPolicyBuilder<Object> rpb = RetryPolicy.builder().withJitter(.2).withJitter(Duration.ofMillis(10));
    assertEquals(rpb.config.jitterFactor, 0.0);
    assertEquals(rpb.config.jitter, Duration.ofMillis(10));
  }

  public void shouldReplaceWithJitterFactor() {
    RetryPolicyBuilder<Object> rpb = RetryPolicy.builder().withJitter(Duration.ofMillis(10)).withJitter(.2);
    assertNull(rpb.config.jitter);
    assertEquals(rpb.config.jitterFactor, .2);
  }

  public void shouldCreateBuilderFromExistingConfig() {
    RetryPolicyConfig<Object> initialConfig = RetryPolicy.builder()
      .withBackoff(Duration.ofMillis(10), Duration.ofMillis(100))
      .withMaxRetries(5)
      .onFailedAttempt(e -> {
      })
      .withJitter(Duration.ofMillis(5)).config;
    RetryPolicyConfig<Object> newConfig = RetryPolicy.builder(initialConfig).config;
    assertEquals(newConfig.delay, Duration.ofMillis(10));
    assertEquals(newConfig.maxDelay, Duration.ofMillis(100));
    assertNotNull(newConfig.failedAttemptListener);
    assertEquals(newConfig.jitter, Duration.ofMillis(5));
  }
}
