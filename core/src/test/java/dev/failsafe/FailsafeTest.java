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

import dev.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;

/**
 * Tests general Failsafe behaviors.
 */
@Test
public class FailsafeTest extends Testing {
  /**
   * Asserts that errors can be reported through Failsafe.
   */
  public void shouldSupportErrors() {
    // Given
    RetryPolicy<Boolean> retryPolicy = RetryPolicy.ofDefaults();

    // When / Then
    testRunFailure(Failsafe.with(retryPolicy), ctx -> {
      throw new InternalError();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
    }, InternalError.class);
  }

  /**
   * Asserts that checked exeptions are wrapped with FailsafeException for sync executions.
   */
  public void shouldWrapCheckedExceptionsForSyncExecutions() {
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder().withMaxRetries(0).build();

    assertThrows(() -> Failsafe.with(retryPolicy).run(() -> {
      throw new TimeoutException();
    }), FailsafeException.class, TimeoutException.class);
  }
}
