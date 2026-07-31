/*
 * Copyright 2024 the original author or authors.
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

import dev.failsafe.Bulkhead;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a Bulkhead does not lose permits when a timed acquisition gives up.
 */
@Test
public class BulkheadImplTest {
  private static final Duration SHORT_WAIT = Duration.ofMillis(20);

  public void shouldRetainPermitAfterAcquireTimesOut() throws Throwable {
    Bulkhead<Object> bulkhead = Bulkhead.builder(1).build();
    assertTrue(bulkhead.tryAcquirePermit());

    assertFalse(bulkhead.tryAcquirePermit(SHORT_WAIT));
    bulkhead.releasePermit();

    assertTrue(bulkhead.tryAcquirePermit(), "the released permit must be available again");
  }

  public void shouldRetainCapacityOverRepeatedAcquireTimeouts() throws Throwable {
    int maxConcurrency = 4;
    Bulkhead<Object> bulkhead = Bulkhead.builder(maxConcurrency).build();

    for (int round = 1; round <= 5; round++) {
      for (int i = 0; i < maxConcurrency; i++)
        assertTrue(bulkhead.tryAcquirePermit());
      assertFalse(bulkhead.tryAcquirePermit(SHORT_WAIT));
      for (int i = 0; i < maxConcurrency; i++)
        bulkhead.releasePermit();

      assertEquals(capacityOf(bulkhead), maxConcurrency, "capacity after round " + round);
    }
  }

  private static int capacityOf(Bulkhead<Object> bulkhead) {
    int permits = 0;
    while (bulkhead.tryAcquirePermit())
      permits++;
    for (int i = 0; i < permits; i++)
      bulkhead.releasePermit();
    return permits;
  }
}
