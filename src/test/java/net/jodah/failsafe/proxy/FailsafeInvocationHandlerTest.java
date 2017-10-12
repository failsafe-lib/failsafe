package net.jodah.failsafe.proxy;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class FailsafeInvocationHandlerTest {

    interface Counter {

        void increment();

        long value();

    }

    private static class FailingCounter implements Counter {

        private final AtomicLong invocations = new AtomicLong(0);
        private final AtomicLong count = new AtomicLong(0);
        private long failUntil;

        public FailingCounter(long failUntil) {
            this.failUntil = failUntil;
        }

        @Override
        public void increment() {
            try {
                if (invocations.get() < failUntil) {
                    throw new RuntimeException(String.format("Failing %s/%s", count.get(), failUntil));
                }
                count.incrementAndGet();
            } finally {
                invocations.incrementAndGet();
            }
        }

        @Override
        public long value() {
            return count.get();
        }

    }

    @Test
    public void testSimpleProxy() {
        FailingCounter failingCounter = new FailingCounter(1);
        Counter counter = Failsafe.with(new RetryPolicy().withMaxRetries(3))
                .proxy(failingCounter, Counter.class);
        counter.increment();
        assertEquals(counter.value(), 1L);
        assertEquals(failingCounter.invocations.get(), 2L);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreatingConcreteProxy() {
        Failsafe.with(new RetryPolicy().withMaxRetries(3))
                .proxy(new LongAdder(), LongAdder.class);
    }

    @Test
    public void testUndeclaredException() {
        FailingCounter failingCounter = new FailingCounter(3);
        Counter counter = Failsafe.with(new RetryPolicy().withMaxRetries(1))
                .withFallback(() -> {
                    throw new IOException("This is a checked exception.");
                })
                .proxy(failingCounter, Counter.class);
        try {
            counter.increment();
            throw new IllegalStateException("This line should be hit.");
        } catch (Throwable t) {
            assertTrue(t instanceof UndeclaredThrowableException);
            assertTrue(t.getCause() instanceof IOException);
        }
    }

}
