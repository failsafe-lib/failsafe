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
import java.util.concurrent.atomic.AtomicInteger;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.util.Duration;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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

    static class UncheckedExpectedException extends RuntimeException {
    }

    static class DelayException extends UncheckedExpectedException {
    }


    @Test(expectedExceptions = NullPointerException.class)
    public void testNullDelayFunction() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay(null);
        fail("Null delay function");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullResultType() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS), null);
        fail("Null delay function result type");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullFailureType() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelayThrowing((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS), null);
        fail("Null delay function failure type");
    }

    @Test
    public void testDynamicDelay() {
        long DELAY = TimeUnit.MILLISECONDS.toNanos(500);
        long PAD = TimeUnit.MILLISECONDS.toNanos(25);

        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay((result, failure, context) -> {
                if (failure instanceof DynamicDelayException)
                    return ((DynamicDelayException) failure).getDuration();
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


    @Test(expectedExceptions = UncheckedExpectedException.class)
    public void testUncheckedExceptionComputingDelay() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay((result, failure, context) -> {
                throw new UncheckedExpectedException();
            });

        Failsafe.with(retryPolicy)
            .run((ExecutionContext context) -> {
                throw new RuntimeException("try again");
            });
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSettingBackoffWhenDelayFunctionAlreadySet() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withDelay((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS))
            .withBackoff(1L, 3L, TimeUnit.SECONDS);
        fail("Delay function already set");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSettingDelayFunctionWhenBackoffAlreadySet() {
        RetryPolicy retryPolicy = new RetryPolicy()
            .withBackoff(1L, 3L, TimeUnit.SECONDS)
            .withDelay((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS));
        fail("Backoff delays already set");
    }

    @Test
    public void testDelayOnMatchingReturnType() {
        AtomicInteger delays = new AtomicInteger(0);
        RetryPolicy retryPolicy = new RetryPolicy()
            .retryIf(result -> true)
            .withMaxRetries(4)
            .withDelay((String r, Throwable f, ExecutionContext c) -> {
                delays.incrementAndGet(); // side-effect for test purposes
                return new Duration(1L, TimeUnit.MICROSECONDS);
            }, String.class);

        AtomicInteger attempts = new AtomicInteger(0);
        Object result = Failsafe.with(retryPolicy)
            .withFallback(123)
            .get(() -> {
                int i = attempts.getAndIncrement();
                switch (i) {
                    case 0:
                    case 3: return "" + i;
                    default: return i;
                }
            });

        assertEquals(result, 123, "Fallback should be used");
        assertEquals(attempts.get(), 5, "Expecting five attempts (1 + 4 retries)");
        assertEquals(delays.get(), 2, "Expecting two dynamic delays matching String result");
    }

    @Test
    public void testDelayOnMatchingFailureType() {
        AtomicInteger delays = new AtomicInteger(0);
        RetryPolicy retryPolicy = new RetryPolicy()
            .retryOn(UncheckedExpectedException.class)
            .withMaxRetries(4)
            .withDelayThrowing((Object r, DelayException f, ExecutionContext c) -> {
                delays.incrementAndGet(); // side-effect for test purposes
                return new Duration(1L, TimeUnit.MICROSECONDS);
            }, DelayException.class);

        AtomicInteger attempts = new AtomicInteger(0);
        int result = Failsafe.with(retryPolicy)
            .withFallback(123)
            .get(() -> {
                int i = attempts.getAndIncrement();
                switch (i) {
                    case 0:
                    case 2: throw new DelayException();
                    default: throw new UncheckedExpectedException();
                }
            });
        assertEquals(result, 123, "Fallback should be used");
        assertEquals(attempts.get(), 5, "Expecting five attempts (1 + 4 retries)");
        assertEquals(delays.get(), 2, "Expecting two dynamic delays matching DelayException failure");
    }
}