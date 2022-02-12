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

import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

@Test
public class TimeoutBuilderTest {
  public void shouldCreateBuilderFromExistingConfig() {
    TimeoutConfig<Object> initialConfig = Timeout.builder(Duration.ofMillis(50)).withInterrupt().onFailure(e -> {
    }).config;
    TimeoutConfig<Object> newConfig = Timeout.builder(initialConfig).config;
    assertEquals(newConfig.timeout, Duration.ofMillis(50));
    assertTrue(newConfig.canInterrupt);
    assertNotNull(newConfig.failureListener);
  }
}
