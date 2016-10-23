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

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

@Test
public class AbstractExecutionTest {
  public void testRandomizeDelayForFactor() {
    assertEquals(AbstractExecution.randomizeDelay(100, .5, 0), 150);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .25), 125);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .5), 100);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .75), 75);
    assertEquals(AbstractExecution.randomizeDelay(100, .5, .9999), 50);

    assertEquals(AbstractExecution.randomizeDelay(500, .5, .25), 625);
    assertEquals(AbstractExecution.randomizeDelay(500, .5, .75), 375);
    assertEquals(AbstractExecution.randomizeDelay(50000, .5, .25), 62500);
  }

  public void testRandomizeDelayForDuration() {
    assertEquals(AbstractExecution.randomizeDelay(100, 50, 0), 150);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .25), 125);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .5), 100);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .75), 75);
    assertEquals(AbstractExecution.randomizeDelay(100, 50, .9999), 50);

    assertEquals(AbstractExecution.randomizeDelay(500, 50, .25), 525);
    assertEquals(AbstractExecution.randomizeDelay(50000, 5000, .25), 52500);
  }
}
