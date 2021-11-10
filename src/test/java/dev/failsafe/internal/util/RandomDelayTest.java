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
package dev.failsafe.internal.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test
public class RandomDelayTest {
  public void testRandomDelayInRange() {
    assertEquals(RandomDelay.randomDelayInRange(10, 100, 0), 10);
    assertEquals(RandomDelay.randomDelayInRange(10, 100, .25), 32);
    assertEquals(RandomDelay.randomDelayInRange(10, 100, .5), 55);
    assertEquals(RandomDelay.randomDelayInRange(10, 100, .75), 77);
    assertEquals(RandomDelay.randomDelayInRange(10, 100, 1), 100);

    assertEquals(RandomDelay.randomDelayInRange(50, 500, .25), 162);
    assertEquals(RandomDelay.randomDelayInRange(5000, 50000, .25), 16250);
  }

  public void testRandomDelayForFactor() {
    assertEquals(RandomDelay.randomDelay(100, .5, 0), 150);
    assertEquals(RandomDelay.randomDelay(100, .5, .25), 125);
    assertEquals(RandomDelay.randomDelay(100, .5, .5), 100);
    assertEquals(RandomDelay.randomDelay(100, .5, .75), 75);
    assertEquals(RandomDelay.randomDelay(100, .5, .9999), 50);

    assertEquals(RandomDelay.randomDelay(500, .5, .25), 625);
    assertEquals(RandomDelay.randomDelay(500, .5, .75), 375);
    assertEquals(RandomDelay.randomDelay(50000, .5, .25), 62500);
  }

  public void testRandomDelayForDuration() {
    assertEquals(RandomDelay.randomDelay(100, 50, 0), 150);
    assertEquals(RandomDelay.randomDelay(100, 50, .25), 125);
    assertEquals(RandomDelay.randomDelay(100, 50, .5), 100);
    assertEquals(RandomDelay.randomDelay(100, 50, .75), 75);
    assertEquals(RandomDelay.randomDelay(100, 50, .9999), 50);

    assertEquals(RandomDelay.randomDelay(500, 50, .25), 525);
    assertEquals(RandomDelay.randomDelay(50000, 5000, .25), 52500);
  }
}
