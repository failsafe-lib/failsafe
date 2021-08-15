/*
 * Copyright 2021 the original author or authors.
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
package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static net.jodah.failsafe.Testing.*;
import static org.testng.Assert.assertEquals;

/**
 * Tests AsyncExecution scenarios.
 */
@Test
public class AsyncExecutionTest {
  public void testAsyncExecWithPassthroughPolicies() {
    Stats rpStats = new Stats();
    RetryPolicy<Object> rp = withStats(new RetryPolicy<>().withMaxRetries(3), rpStats, true);
    // Passthrough policy that should allow async execution results through
    Fallback<Object> fb = Fallback.<Object>of("test").handleIf((r, f) -> false);
    Timeout<Object> timeout = Timeout.of(Duration.ofMinutes(1));
    AtomicInteger counter = new AtomicInteger();

    Consumer<FailsafeExecutor<Object>> test = failsafe -> testAsyncExecutionSuccess(failsafe, ex -> {
      runAsync(() -> {
        System.out.println("Executing");
        if (counter.getAndIncrement() < 3)
          ex.retryOn(new IllegalStateException());
        else
          ex.complete();
      });
    }, e -> {
      assertEquals(e.getAttemptCount(), 4);
      assertEquals(e.getExecutionCount(), 4);
      assertEquals(rpStats.failedAttemptCount, 3);
      assertEquals(rpStats.retryCount, 3);
    }, null);

    // Test RetryPolicy, Fallback
    test.accept(Failsafe.with(rp, fb));

    // Test RetryPolicy, Timeout
    rpStats.reset();
    counter.set(0);
    test.accept(Failsafe.with(rp, timeout));
  }
}
