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
package net.jodah.failsafe.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.OpenState;

@Test
public class OpenStateTest {
  public void testAllowsExecution() throws Throwable {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withDelay(100, TimeUnit.MILLISECONDS);
    breaker.open();
    OpenState state = new OpenState(breaker);
    assertTrue(breaker.isOpen());
    assertFalse(state.allowsExecution(null));

    // When
    Thread.sleep(110);

    // Then
    assertTrue(state.allowsExecution(null));
    assertEquals(breaker.getState(), State.HALF_OPEN);
  }
}
