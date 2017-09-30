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
package net.jodah.failsafe.dyndelay;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.util.Duration;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class DynamicDelayTest {

    static class DynamicDelayException extends Exception {
        final Duration duration;

        DynamicDelayException(long time, TimeUnit unit) {
            super(String.format("Dynamic delay of %s %s", time, unit));
            this.duration = new Duration(time, unit);
        }

        public Duration getDuration() {
            return duration;
        }
    }

    @Test
    public void testDynamicDelay() {
        long DELAY = TimeUnit.MILLISECONDS.toNanos(500);
        long PAD = TimeUnit.MILLISECONDS.toNanos(25);

        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelayFunction((result, exception) -> {
                if (exception instanceof DynamicDelayException)
                    return ((DynamicDelayException) exception).getDuration();
                else
                    return null;
            })
            .withMaxRetries(2);

        List<Long> executionTimes = new ArrayList<>();

        Failsafe.with(retryPolicy)
            .run((ExecutionContext context) -> {
                executionTimes.add(System.nanoTime());
                if (context.getExecutions() == 0)
                    throw new DynamicDelayException(DELAY, TimeUnit.NANOSECONDS);
            });

        assertEquals(executionTimes.size(), 2, "Should have exactly two executions");
        long t0 = executionTimes.get(0);
        long t1 = executionTimes.get(1);
        //System.out.printf("actual delay %d, expected %d%n",
        //    TimeUnit.NANOSECONDS.toMillis(t1 - t0),
        //    TimeUnit.NANOSECONDS.toMillis(DELAY));
        assertTrue(t1 - t0 > DELAY - PAD, "Time between executions less than expected");
        assertTrue(t1 - t0 < DELAY + PAD, "Time between executions more than expected");
    }
}
